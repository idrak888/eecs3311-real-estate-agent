package ca.yorku.realestate.domain;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Builder for {@link SearchCriteria}.
 *
 * <p>Criteria arrive piecemeal: one form control at a time in the GUI, one flag at a time in
 * the CLI, or one extracted field at a time from the LLM's JSON. The builder lets each source
 * set only what it has, and produces one immutable SearchCriteria at the end.
 *
 * <p>Text values are normalized here (trimmed; features lower-cased) so equal criteria
 * compare equal regardless of where they came from. Blank text is ignored rather than stored.
 */
public class SearchCriteriaBuilder {

    private Double minPrice;
    private Double maxPrice;
    private Integer minBedrooms;
    private Integer minBathrooms;
    private final Set<PropertyType> types = EnumSet.noneOf(PropertyType.class);
    private final Set<String> neighborhoods = new LinkedHashSet<>();
    private final Set<String> requiredFeatures = new LinkedHashSet<>();

    public SearchCriteriaBuilder() {
    }

    /** Starts from existing criteria, e.g. to change one field of the session criteria. */
    public static SearchCriteriaBuilder from(SearchCriteria criteria) {
        SearchCriteriaBuilder b = new SearchCriteriaBuilder();
        b.minPrice = criteria.getMinPrice();
        b.maxPrice = criteria.getMaxPrice();
        b.minBedrooms = criteria.getMinBedrooms();
        b.minBathrooms = criteria.getMinBathrooms();
        b.types.addAll(criteria.getTypes());
        b.neighborhoods.addAll(criteria.getNeighborhoods());
        b.requiredFeatures.addAll(criteria.getRequiredFeatures());
        return b;
    }

    /** @param minPrice lower bound, or {@code null} to clear it */
    public SearchCriteriaBuilder withMinPrice(Double minPrice) {
        this.minPrice = minPrice;
        return this;
    }

    /** @param maxPrice upper bound, or {@code null} to clear it */
    public SearchCriteriaBuilder withMaxPrice(Double maxPrice) {
        this.maxPrice = maxPrice;
        return this;
    }

    public SearchCriteriaBuilder withMinBedrooms(Integer minBedrooms) {
        this.minBedrooms = minBedrooms;
        return this;
    }

    public SearchCriteriaBuilder withMinBathrooms(Integer minBathrooms) {
        this.minBathrooms = minBathrooms;
        return this;
    }

    /** Adds an acceptable property type; several types are combined with OR. */
    public SearchCriteriaBuilder withType(PropertyType type) {
        if (type != null) {
            types.add(type);
        }
        return this;
    }

    /** Adds an acceptable neighborhood; several are combined with OR. */
    public SearchCriteriaBuilder withNeighborhood(String neighborhood) {
        if (neighborhood != null && !neighborhood.isBlank()) {
            neighborhoods.add(neighborhood.trim());
        }
        return this;
    }

    /** Adds a required feature; all required features must be present. */
    public SearchCriteriaBuilder withFeature(String feature) {
        if (feature != null && !feature.isBlank()) {
            requiredFeatures.add(feature.trim().toLowerCase(Locale.ROOT));
        }
        return this;
    }

    public SearchCriteriaBuilder clearTypes() {
        types.clear();
        return this;
    }

    public SearchCriteriaBuilder clearNeighborhoods() {
        neighborhoods.clear();
        return this;
    }

    public SearchCriteriaBuilder clearFeatures() {
        requiredFeatures.clear();
        return this;
    }

    /** Creates the criteria. The builder can keep being used afterwards without affecting it. */
    public SearchCriteria build() {
        return new SearchCriteria(minPrice, maxPrice, minBedrooms, minBathrooms, types,
                neighborhoods, requiredFeatures);
    }
}
