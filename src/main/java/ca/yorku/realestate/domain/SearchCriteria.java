package ca.yorku.realestate.domain;

import ca.yorku.realestate.domain.filter.AndFilter;
import ca.yorku.realestate.domain.filter.BathroomFilter;
import ca.yorku.realestate.domain.filter.BedroomFilter;
import ca.yorku.realestate.domain.filter.FeatureFilter;
import ca.yorku.realestate.domain.filter.Filter;
import ca.yorku.realestate.domain.filter.NeighborhoodFilter;
import ca.yorku.realestate.domain.filter.OrFilter;
import ca.yorku.realestate.domain.filter.PriceFilter;
import ca.yorku.realestate.domain.filter.TypeFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable, possibly partial set of search criteria.
 *
 * <p>Every field is optional: {@code null} numbers and empty sets mean "no constraint".
 * Instances are created with {@link SearchCriteriaBuilder}, from form controls and CLI flags
 * (F03) or from LLM extraction across several conversation turns (F02).
 *
 * <p>Construction does not reject contradictory values such as an inverted price range;
 * {@link #isValid()} reports them so the caller can flag the problem instead of searching.
 */
public final class SearchCriteria {

    private final Double minPrice;
    private final Double maxPrice;
    private final Integer minBedrooms;
    private final Integer minBathrooms;
    private final Set<PropertyType> types;
    private final Set<String> neighborhoods;
    private final Set<String> requiredFeatures;

    SearchCriteria(Double minPrice, Double maxPrice, Integer minBedrooms, Integer minBathrooms,
                   Set<PropertyType> types, Set<String> neighborhoods, Set<String> requiredFeatures) {
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.minBedrooms = minBedrooms;
        this.minBathrooms = minBathrooms;
        this.types = types.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(PropertyType.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(types));
        this.neighborhoods = Collections.unmodifiableSet(new LinkedHashSet<>(neighborhoods));
        this.requiredFeatures = Collections.unmodifiableSet(new LinkedHashSet<>(requiredFeatures));
    }

    /** Criteria with no constraints; its filter matches every listing. */
    public static SearchCriteria empty() {
        return new SearchCriteriaBuilder().build();
    }

    public Double getMinPrice() {
        return minPrice;
    }

    public Double getMaxPrice() {
        return maxPrice;
    }

    public Integer getMinBedrooms() {
        return minBedrooms;
    }

    public Integer getMinBathrooms() {
        return minBathrooms;
    }

    public Set<PropertyType> getTypes() {
        return types;
    }

    public Set<String> getNeighborhoods() {
        return neighborhoods;
    }

    public Set<String> getRequiredFeatures() {
        return requiredFeatures;
    }

    /** True when no field is set. */
    public boolean isEmpty() {
        return minPrice == null && maxPrice == null && minBedrooms == null && minBathrooms == null
                && types.isEmpty() && neighborhoods.isEmpty() && requiredFeatures.isEmpty();
    }

    /** True when the criteria can be searched; see {@link #validationErrors()} for reasons. */
    public boolean isValid() {
        return validationErrors().isEmpty();
    }

    /** Human-readable problems that prevent searching, e.g. an inverted price range. */
    public List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        if (minPrice != null && minPrice < 0) {
            errors.add("Minimum price cannot be negative");
        }
        if (maxPrice != null && maxPrice < 0) {
            errors.add("Maximum price cannot be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            errors.add(String.format(java.util.Locale.ROOT,
                    "Minimum price ($%,.0f) exceeds maximum price ($%,.0f)", minPrice, maxPrice));
        }
        if (minBedrooms != null && minBedrooms < 0) {
            errors.add("Minimum bedrooms cannot be negative");
        }
        if (minBathrooms != null && minBathrooms < 0) {
            errors.add("Minimum bathrooms cannot be negative");
        }
        return errors;
    }

    /**
     * Builds the Composite filter tree for these criteria: an {@link AndFilter} at the root with
     * one child per active criterion, and an {@link OrFilter} where a field accepts several
     * values (e.g. condo OR townhouse). Empty criteria give an empty AndFilter, which matches
     * every listing.
     *
     * @throws IllegalStateException if the criteria are not {@linkplain #isValid() valid}
     */
    public Filter toFilter() {
        if (!isValid()) {
            throw new IllegalStateException("Invalid criteria: " + String.join("; ", validationErrors()));
        }
        AndFilter root = new AndFilter();
        if (minPrice != null || maxPrice != null) {
            root.add(new PriceFilter(minPrice, maxPrice));
        }
        if (minBedrooms != null) {
            root.add(new BedroomFilter(minBedrooms));
        }
        if (minBathrooms != null) {
            root.add(new BathroomFilter(minBathrooms));
        }
        if (!types.isEmpty()) {
            root.add(anyOf(types.stream().map(t -> (Filter) new TypeFilter(t)).toList()));
        }
        if (!neighborhoods.isEmpty()) {
            root.add(anyOf(neighborhoods.stream().map(n -> (Filter) new NeighborhoodFilter(n)).toList()));
        }
        if (!requiredFeatures.isEmpty()) {
            root.add(new FeatureFilter(requiredFeatures));
        }
        return root;
    }

    private static Filter anyOf(List<Filter> options) {
        if (options.size() == 1) {
            return options.get(0);
        }
        OrFilter or = new OrFilter();
        options.forEach(or::add);
        return or;
    }

    /**
     * Combines these criteria with a newer set, as across conversation turns (F02): a field set
     * in {@code newer} overrides this one, and a field {@code newer} leaves unset persists.
     * Neither object is modified.
     */
    public SearchCriteria merge(SearchCriteria newer) {
        if (newer == null) {
            return this;
        }
        return new SearchCriteria(
                newer.minPrice != null ? newer.minPrice : minPrice,
                newer.maxPrice != null ? newer.maxPrice : maxPrice,
                newer.minBedrooms != null ? newer.minBedrooms : minBedrooms,
                newer.minBathrooms != null ? newer.minBathrooms : minBathrooms,
                newer.types.isEmpty() ? types : newer.types,
                newer.neighborhoods.isEmpty() ? neighborhoods : newer.neighborhoods,
                newer.requiredFeatures.isEmpty() ? requiredFeatures : newer.requiredFeatures);
    }

    /** Readable summary of the active criteria, e.g. for the GUI criteria panel or CLI. */
    public String describe() {
        return isValid() ? toFilter().describe() : "invalid: " + String.join("; ", validationErrors());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchCriteria)) {
            return false;
        }
        SearchCriteria c = (SearchCriteria) o;
        return Objects.equals(minPrice, c.minPrice) && Objects.equals(maxPrice, c.maxPrice)
                && Objects.equals(minBedrooms, c.minBedrooms)
                && Objects.equals(minBathrooms, c.minBathrooms)
                && types.equals(c.types) && neighborhoods.equals(c.neighborhoods)
                && requiredFeatures.equals(c.requiredFeatures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minPrice, maxPrice, minBedrooms, minBathrooms, types, neighborhoods,
                requiredFeatures);
    }

    @Override
    public String toString() {
        return "SearchCriteria[" + describe() + "]";
    }
}
