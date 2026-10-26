package ca.yorku.realestate.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.domain.filter.AndFilter;
import ca.yorku.realestate.domain.filter.Filter;
import ca.yorku.realestate.domain.filter.OrFilter;
import ca.yorku.realestate.domain.filter.TypeFilter;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SearchCriteriaTest {

    private static final Listing HOUSE = new Listing("H1", 850_000, 3, 2, 1700, PropertyType.HOUSE,
            "Maple", 2005, List.of("garage", "backyard"));
    private static final Listing CONDO = new Listing("C1", 620_000, 2, 1, 900, PropertyType.CONDO,
            "Downtown", 2018, List.of("gym"));
    private static final Listing TOWN = new Listing("T1", 710_000, 3, 3, 1400, PropertyType.TOWNHOUSE,
            "Woodbridge", 2012, List.of("garage"));

    // ---- builder ----

    @Test
    void builderSetsEveryField() {
        SearchCriteria c = new SearchCriteriaBuilder()
                .withMinPrice(500_000.0).withMaxPrice(850_000.0)
                .withMinBedrooms(3).withMinBathrooms(2)
                .withType(PropertyType.HOUSE).withType(PropertyType.TOWNHOUSE)
                .withNeighborhood(" Maple ").withFeature(" Garage ")
                .build();
        assertEquals(500_000.0, c.getMinPrice());
        assertEquals(850_000.0, c.getMaxPrice());
        assertEquals(Integer.valueOf(3), c.getMinBedrooms());
        assertEquals(Integer.valueOf(2), c.getMinBathrooms());
        assertEquals(Set.of(PropertyType.HOUSE, PropertyType.TOWNHOUSE), c.getTypes());
        assertEquals(Set.of("Maple"), c.getNeighborhoods());
        assertEquals(Set.of("garage"), c.getRequiredFeatures());
    }

    @Test
    void builderIgnoresBlankAndNullValues() {
        SearchCriteria c = new SearchCriteriaBuilder()
                .withType(null).withNeighborhood("  ").withFeature(null).build();
        assertTrue(c.isEmpty());
    }

    @Test
    void builtCriteriaAreUnaffectedByLaterBuilderUse() {
        SearchCriteriaBuilder b = new SearchCriteriaBuilder().withFeature("pool");
        SearchCriteria first = b.build();
        b.withFeature("gym").withMaxPrice(1.0);
        assertEquals(Set.of("pool"), first.getRequiredFeatures());
        assertEquals(null, first.getMaxPrice());
    }

    @Test
    void builtCriteriaSetsAreUnmodifiable() {
        SearchCriteria c = new SearchCriteriaBuilder().withFeature("pool").build();
        assertThrows(UnsupportedOperationException.class, () -> c.getRequiredFeatures().add("gym"));
        assertThrows(UnsupportedOperationException.class, () -> c.getTypes().add(PropertyType.HOUSE));
    }

    @Test
    void fromCopiesExistingCriteriaForEditing() {
        SearchCriteria original = new SearchCriteriaBuilder()
                .withMaxPrice(800_000.0).withType(PropertyType.CONDO).build();
        SearchCriteria edited = SearchCriteriaBuilder.from(original).withMinBedrooms(2).build();
        assertEquals(800_000.0, edited.getMaxPrice());
        assertEquals(Set.of(PropertyType.CONDO), edited.getTypes());
        assertEquals(Integer.valueOf(2), edited.getMinBedrooms());
        assertEquals(null, original.getMinBedrooms());
    }

    @Test
    void clearMethodsRemoveMultiValueFields() {
        SearchCriteria c = SearchCriteriaBuilder.from(new SearchCriteriaBuilder()
                        .withType(PropertyType.CONDO).withNeighborhood("X").withFeature("gym").build())
                .clearTypes().clearNeighborhoods().clearFeatures().build();
        assertTrue(c.isEmpty());
    }

    // ---- validation ----

    @Test
    void invertedPriceRangeIsInvalidAndBlocksFilter() {
        SearchCriteria c = new SearchCriteriaBuilder().withMinPrice(900_000.0).withMaxPrice(500_000.0).build();
        assertFalse(c.isValid());
        assertEquals(List.of("Minimum price ($900,000) exceeds maximum price ($500,000)"),
                c.validationErrors());
        assertThrows(IllegalStateException.class, c::toFilter);
        assertTrue(c.describe().startsWith("invalid:"));
    }

    @Test
    void negativeValuesAreInvalid() {
        SearchCriteria c = new SearchCriteriaBuilder()
                .withMinPrice(-1.0).withMinBedrooms(-1).withMinBathrooms(-2).build();
        assertEquals(3, c.validationErrors().size());
    }

    @Test
    void equalMinAndMaxPriceIsValid() {
        assertTrue(new SearchCriteriaBuilder().withMinPrice(5.0).withMaxPrice(5.0).build().isValid());
    }

    // ---- toFilter ----

    @Test
    void emptyCriteriaMatchEverything() {
        Filter f = SearchCriteria.empty().toFilter();
        assertTrue(f.matches(HOUSE) && f.matches(CONDO) && f.matches(TOWN));
        assertEquals("all listings", SearchCriteria.empty().describe());
    }

    @Test
    void toFilterBuildsAndRootWithOrForMultipleTypes() {
        SearchCriteria c = new SearchCriteriaBuilder()
                .withMaxPrice(800_000.0).withMinBedrooms(2)
                .withType(PropertyType.CONDO).withType(PropertyType.TOWNHOUSE).build();
        Filter f = c.toFilter();

        AndFilter root = assertInstanceOf(AndFilter.class, f);
        assertEquals(3, root.getChildren().size());
        OrFilter types = assertInstanceOf(OrFilter.class, root.getChildren().get(2));
        assertEquals(2, types.getChildren().size());

        assertFalse(f.matches(HOUSE));
        assertTrue(f.matches(CONDO));
        assertTrue(f.matches(TOWN));
        assertEquals("price <= 800000 AND bedrooms >= 2 AND (type condo OR type townhouse)", c.describe());
    }

    @Test
    void singleTypeIsALeafNotAnOr() {
        AndFilter root = (AndFilter) new SearchCriteriaBuilder().withType(PropertyType.HOUSE).build().toFilter();
        assertInstanceOf(TypeFilter.class, root.getChildren().get(0));
    }

    @Test
    void toFilterAppliesBathroomsNeighborhoodsAndFeatures() {
        Filter f = new SearchCriteriaBuilder()
                .withMinBathrooms(2).withNeighborhood("maple").withNeighborhood("WOODBRIDGE")
                .withFeature("garage").build().toFilter();
        assertTrue(f.matches(HOUSE));
        assertTrue(f.matches(TOWN));
        assertFalse(f.matches(CONDO));
    }

    // ---- merge (F02 multi-turn) ----

    @Test
    void mergeOverridesSetFieldsAndKeepsUnmentionedOnes() {
        SearchCriteria turn1 = new SearchCriteriaBuilder()
                .withMinBedrooms(3).withType(PropertyType.HOUSE).withMaxPrice(850_000.0).build();
        SearchCriteria turn2 = new SearchCriteriaBuilder()
                .withMaxPrice(900_000.0).withFeature("garage").build();

        SearchCriteria merged = turn1.merge(turn2);
        assertEquals(900_000.0, merged.getMaxPrice(), "later value overrides");
        assertEquals(Integer.valueOf(3), merged.getMinBedrooms(), "unmentioned field persists");
        assertEquals(Set.of(PropertyType.HOUSE), merged.getTypes());
        assertEquals(Set.of("garage"), merged.getRequiredFeatures());
        assertEquals(850_000.0, turn1.getMaxPrice(), "inputs are not modified");
    }

    @Test
    void mergeReplacesMultiValueFieldsWhenNewerSetsThem() {
        SearchCriteria a = new SearchCriteriaBuilder().withType(PropertyType.HOUSE).build();
        SearchCriteria b = new SearchCriteriaBuilder().withType(PropertyType.CONDO).build();
        assertEquals(Set.of(PropertyType.CONDO), a.merge(b).getTypes());
    }

    @Test
    void mergeWithNullOrEmptyReturnsEquivalentCriteria() {
        SearchCriteria a = new SearchCriteriaBuilder().withMinBedrooms(2).build();
        assertSame(a, a.merge(null));
        assertEquals(a, a.merge(SearchCriteria.empty()));
        assertEquals(a, SearchCriteria.empty().merge(a));
    }

    @Test
    void equalityIsByValue() {
        SearchCriteria a = new SearchCriteriaBuilder().withFeature("Pool").withMinPrice(1.0).build();
        SearchCriteria b = new SearchCriteriaBuilder().withMinPrice(1.0).withFeature("pool").build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
