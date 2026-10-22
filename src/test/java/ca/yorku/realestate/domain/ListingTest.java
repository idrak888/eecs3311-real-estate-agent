package ca.yorku.realestate.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListingTest {

    private static Listing listing(double price, int sqft) {
        return new Listing("L1", price, 3, 2, sqft, PropertyType.HOUSE, "Maple", 2000,
                List.of("Garage", "backyard"));
    }

    @Test
    void pricePerSqFtDividesPriceBySize() {
        assertEquals(500.0, listing(750_000, 1500).getPricePerSqFt(), 1e-9);
    }

    @Test
    void pricePerSqFtIsNaNWhenSizeUnknown() {
        Listing l = listing(750_000, Listing.UNKNOWN_SQUARE_FEET);
        assertFalse(l.hasSquareFeet());
        assertTrue(Double.isNaN(l.getPricePerSqFt()));
    }

    @Test
    void ageIsCurrentYearMinusYearBuilt() {
        assertEquals(26, listing(500_000, 1000).getAge(2026));
    }

    @Test
    void ageNeverNegativeForFutureYearBuilt() {
        assertEquals(0, listing(500_000, 1000).getAge(1999));
    }

    @Test
    void featureLookupIsCaseInsensitive() {
        Listing l = listing(500_000, 1000);
        assertTrue(l.hasFeature("garage"));
        assertTrue(l.hasFeature("  BACKYARD "));
        assertFalse(l.hasFeature("pool"));
        assertFalse(l.hasFeature(null));
    }

    @Test
    void featuresAreNormalizedAndDeduplicated() {
        Listing l = new Listing("L2", 1, 0, 0, 0, PropertyType.CONDO, "X", 2010,
                Arrays.asList("Pool", "pool", " ", null, "Gym"));
        assertEquals(List.of("pool", "gym"), l.getFeatures());
    }

    @Test
    void nullFeaturesBecomeEmptyList() {
        Listing l = new Listing("L3", 1, 0, 0, 0, PropertyType.CONDO, "X", 2010, null);
        assertTrue(l.getFeatures().isEmpty());
    }

    @Test
    void rejectsNonPositivePrice() {
        assertThrows(IllegalArgumentException.class, () -> listing(0, 1000));
        assertThrows(IllegalArgumentException.class, () -> listing(-5, 1000));
        assertThrows(IllegalArgumentException.class, () -> listing(Double.NaN, 1000));
    }

    @Test
    void rejectsNegativeCounts() {
        assertThrows(IllegalArgumentException.class, () ->
                new Listing("L", 1, -1, 0, 0, PropertyType.HOUSE, "X", 2000, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Listing("L", 1, 0, -1, 0, PropertyType.HOUSE, "X", 2000, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Listing("L", 1, 0, 0, -1, PropertyType.HOUSE, "X", 2000, null));
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThrows(IllegalArgumentException.class, () ->
                new Listing(" ", 1, 0, 0, 0, PropertyType.HOUSE, "X", 2000, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Listing("L", 1, 0, 0, 0, null, "X", 2000, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Listing("L", 1, 0, 0, 0, PropertyType.HOUSE, "", 2000, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Listing("L", 1, 0, 0, 0, PropertyType.HOUSE, "X", 1700, null));
    }

    @Test
    void equalityIsById() {
        Listing a = listing(1, 1);
        Listing b = new Listing("L1", 999, 1, 1, 1, PropertyType.CONDO, "Y", 1990, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void propertyTypeParsesCaseInsensitively() {
        assertEquals(PropertyType.TOWNHOUSE, PropertyType.fromString(" townhouse "));
        assertThrows(IllegalArgumentException.class, () -> PropertyType.fromString("castle"));
        assertThrows(IllegalArgumentException.class, () -> PropertyType.fromString(null));
    }
}
