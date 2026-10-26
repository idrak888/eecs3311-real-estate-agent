package ca.yorku.realestate.domain.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FilterTest {

    private static final Listing HOUSE = new Listing("H1", 850_000, 3, 2, 1700, PropertyType.HOUSE,
            "Maple", 2005, List.of("garage", "backyard"));
    private static final Listing CONDO = new Listing("C1", 620_000, 2, 1, 900, PropertyType.CONDO,
            "Downtown", 2018, List.of("gym"));
    private static final Listing TOWN = new Listing("T1", 710_000, 3, 3, 1400, PropertyType.TOWNHOUSE,
            "Woodbridge", 2012, List.of("garage"));

    // ---- leaves ----

    @Test
    void priceFilterIsInclusiveAtBothEnds() {
        PriceFilter f = new PriceFilter(620_000.0, 850_000.0);
        assertTrue(f.matches(HOUSE));
        assertTrue(f.matches(CONDO));
        assertFalse(new PriceFilter(620_001.0, null).matches(CONDO));
        assertFalse(new PriceFilter(null, 849_999.0).matches(HOUSE));
    }

    @Test
    void priceFilterOpenBoundsMatchEverything() {
        PriceFilter f = new PriceFilter(null, null);
        assertTrue(f.matches(HOUSE));
        assertTrue(f.matches(CONDO));
    }

    @Test
    void priceFilterRejectsInvertedOrNegativeRange() {
        assertThrows(IllegalArgumentException.class, () -> new PriceFilter(900_000.0, 500_000.0));
        assertThrows(IllegalArgumentException.class, () -> new PriceFilter(-1.0, null));
    }

    @Test
    void priceFilterDescribesEachBoundShape() {
        assertEquals("price 500000-850000", new PriceFilter(500_000.0, 850_000.0).describe());
        assertEquals("price <= 850000", new PriceFilter(null, 850_000.0).describe());
        assertEquals("price >= 500000", new PriceFilter(500_000.0, null).describe());
    }

    @Test
    void bedroomAndBathroomFiltersAreMinimums() {
        assertTrue(new BedroomFilter(3).matches(HOUSE));
        assertFalse(new BedroomFilter(3).matches(CONDO));
        assertTrue(new BathroomFilter(2).matches(HOUSE));
        assertFalse(new BathroomFilter(2).matches(CONDO));
        assertTrue(new BathroomFilter(0).matches(CONDO));
        assertThrows(IllegalArgumentException.class, () -> new BedroomFilter(-1));
        assertThrows(IllegalArgumentException.class, () -> new BathroomFilter(-1));
    }

    @Test
    void typeFilterAcceptsAnyAllowedType() {
        TypeFilter f = new TypeFilter(EnumSet.of(PropertyType.CONDO, PropertyType.TOWNHOUSE));
        assertFalse(f.matches(HOUSE));
        assertTrue(f.matches(CONDO));
        assertTrue(f.matches(TOWN));
        assertThrows(IllegalArgumentException.class, () -> new TypeFilter(Set.of()));
    }

    @Test
    void neighborhoodFilterIgnoresCaseAndWhitespace() {
        NeighborhoodFilter f = new NeighborhoodFilter(Set.of("  downtown ", "MAPLE"));
        assertTrue(f.matches(HOUSE));
        assertTrue(f.matches(CONDO));
        assertFalse(f.matches(TOWN));
        assertThrows(IllegalArgumentException.class, () -> new NeighborhoodFilter(Set.of(" ")));
    }

    @Test
    void featureFilterRequiresEveryFeature() {
        assertTrue(new FeatureFilter("Garage").matches(TOWN));
        FeatureFilter both = new FeatureFilter(Set.of("garage", "backyard"));
        assertTrue(both.matches(HOUSE));
        assertFalse(both.matches(TOWN));
        assertThrows(IllegalArgumentException.class, () -> new FeatureFilter(Set.of()));
    }

    // ---- composites ----

    @Test
    void emptyAndMatchesEverythingEmptyOrMatchesNothing() {
        assertTrue(new AndFilter().matches(HOUSE));
        assertFalse(new OrFilter().matches(HOUSE));
        assertEquals("all listings", new AndFilter().describe());
    }

    @Test
    void andRequiresAllChildren() {
        AndFilter f = new AndFilter(new BedroomFilter(3), new FeatureFilter("garage"));
        assertTrue(f.matches(HOUSE));
        assertTrue(f.matches(TOWN));
        assertFalse(f.matches(CONDO));
    }

    @Test
    void orRequiresAnyChild() {
        OrFilter f = new OrFilter(new TypeFilter(PropertyType.CONDO), new TypeFilter(PropertyType.TOWNHOUSE));
        assertFalse(f.matches(HOUSE));
        assertTrue(f.matches(CONDO));
        assertTrue(f.matches(TOWN));
    }

    @Test
    void nestedTreeEvaluatesPriceAndBedsAndCondoOrTownhouse() {
        AndFilter root = new AndFilter();
        root.add(new PriceFilter(null, 800_000.0));
        root.add(new BedroomFilter(2));
        root.add(new OrFilter(new TypeFilter(PropertyType.CONDO), new TypeFilter(PropertyType.TOWNHOUSE)));

        assertFalse(root.matches(HOUSE), "house fails both price and type");
        assertTrue(root.matches(CONDO));
        assertTrue(root.matches(TOWN));
        assertEquals("price <= 800000 AND bedrooms >= 2 AND (type condo OR type townhouse)",
                root.describe());
    }

    @Test
    void compositeRejectsNullChildAndExposesCopyOfChildren() {
        AndFilter f = new AndFilter(new BedroomFilter(1));
        assertThrows(IllegalArgumentException.class, () -> f.add(null));
        List<Filter> children = f.getChildren();
        f.add(new BathroomFilter(1));
        assertEquals(1, children.size());
        assertEquals(2, f.getChildren().size());
    }
}
