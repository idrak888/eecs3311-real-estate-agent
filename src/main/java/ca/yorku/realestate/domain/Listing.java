package ca.yorku.realestate.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable property record.
 *
 * <p>Construction validates every field, so an invalid Listing cannot exist. Parsers rely on
 * this: they attempt construction and treat the resulting exception as the rejection reason.
 *
 * <p>Square footage is optional in the source data. A value of {@link #UNKNOWN_SQUARE_FEET}
 * means it was not supplied; derived values that depend on it report themselves unavailable
 * rather than dividing by zero.
 */
public final class Listing {

    /** Sentinel for a listing whose square footage was not supplied. */
    public static final int UNKNOWN_SQUARE_FEET = 0;

    /** Earliest year built the validator accepts. */
    public static final int MIN_YEAR_BUILT = 1800;

    private final String id;
    private final double price;
    private final int bedrooms;
    private final int bathrooms;
    private final int squareFeet;
    private final PropertyType type;
    private final String neighborhood;
    private final int yearBuilt;
    private final List<String> features;

    public Listing(String id, double price, int bedrooms, int bathrooms, int squareFeet,
                   PropertyType type, String neighborhood, int yearBuilt, List<String> features) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (!(price > 0) || Double.isInfinite(price)) {
            throw new IllegalArgumentException("price must be positive (was " + price + ")");
        }
        if (bedrooms < 0) {
            throw new IllegalArgumentException("bedrooms must be non-negative (was " + bedrooms + ")");
        }
        if (bathrooms < 0) {
            throw new IllegalArgumentException("bathrooms must be non-negative (was " + bathrooms + ")");
        }
        if (squareFeet < 0) {
            throw new IllegalArgumentException("squareFeet must be non-negative (was " + squareFeet + ")");
        }
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (neighborhood == null || neighborhood.isBlank()) {
            throw new IllegalArgumentException("neighborhood is required");
        }
        if (yearBuilt < MIN_YEAR_BUILT) {
            throw new IllegalArgumentException("yearBuilt must be " + MIN_YEAR_BUILT
                    + " or later (was " + yearBuilt + ")");
        }

        this.id = id.trim();
        this.price = price;
        this.bedrooms = bedrooms;
        this.bathrooms = bathrooms;
        this.squareFeet = squareFeet;
        this.type = type;
        this.neighborhood = neighborhood.trim();
        this.yearBuilt = yearBuilt;
        this.features = normalizeFeatures(features);
    }

    private static List<String> normalizeFeatures(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String f : raw) {
            if (f != null && !f.isBlank()) {
                cleaned.add(f.trim().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(cleaned);
    }

    public String getId() {
        return id;
    }

    public double getPrice() {
        return price;
    }

    public int getBedrooms() {
        return bedrooms;
    }

    public int getBathrooms() {
        return bathrooms;
    }

    public int getSquareFeet() {
        return squareFeet;
    }

    public boolean hasSquareFeet() {
        return squareFeet != UNKNOWN_SQUARE_FEET;
    }

    public PropertyType getType() {
        return type;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public int getYearBuilt() {
        return yearBuilt;
    }

    /** Features in lower case, de-duplicated, in source order. */
    public List<String> getFeatures() {
        return Collections.unmodifiableList(features);
    }

    /**
     * Price per square foot.
     *
     * @return the ratio, or {@link Double#NaN} when square footage is unknown. Callers should
     *     check {@link #hasSquareFeet()} and display "unavailable" rather than the NaN.
     */
    public double getPricePerSqFt() {
        if (!hasSquareFeet()) {
            return Double.NaN;
        }
        return price / squareFeet;
    }

    /**
     * Age of the property in the given year. Never negative: a listing built in the future
     * relative to {@code currentYear} (e.g. pre-construction) reports age zero.
     */
    public int getAge(int currentYear) {
        return Math.max(0, currentYear - yearBuilt);
    }

    /** Case-insensitive feature check. */
    public boolean hasFeature(String feature) {
        if (feature == null) {
            return false;
        }
        return features.contains(feature.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Listing)) {
            return false;
        }
        return id.equals(((Listing) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s: %s in %s, $%,.0f, %d bed / %d bath",
                id, type, neighborhood, price, bedrooms, bathrooms);
    }
}
