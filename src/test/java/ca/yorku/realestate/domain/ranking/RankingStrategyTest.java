package ca.yorku.realestate.domain.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.domain.SearchCriteriaBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class RankingStrategyTest {

    private static Listing listing(String id, double price, int beds, int sqft, PropertyType type,
                                   String hood, String... features) {
        return new Listing(id, price, beds, 2, sqft, type, hood, 2010, List.of(features));
    }

    // C: cheapest, B and D tie on price, A most expensive; E has unknown sqft.
    private static final Listing A = listing("A", 900_000, 4, 2000, PropertyType.HOUSE, "Maple", "garage");
    private static final Listing B = listing("B", 700_000, 3, 1000, PropertyType.TOWNHOUSE, "Maple");
    private static final Listing C = listing("C", 500_000, 2, 800, PropertyType.CONDO, "Downtown", "gym");
    private static final Listing D = listing("D", 700_000, 3, 1400, PropertyType.HOUSE, "Vaughan", "garage", "pool");
    private static final Listing E = listing("E", 400_000, 1, Listing.UNKNOWN_SQUARE_FEET,
            PropertyType.CONDO, "Downtown");

    private static final List<Listing> ALL = List.of(A, B, C, D, E);

    private static List<String> ids(List<Listing> listings) {
        return listings.stream().map(Listing::getId).toList();
    }

    @Test
    void priceAscendingCheapestFirstTiesById() {
        assertEquals(List.of("E", "C", "B", "D", "A"), ids(new PriceAscendingStrategy().rank(ALL, null)));
    }

    @Test
    void priceDescendingMostExpensiveFirstTiesById() {
        assertEquals(List.of("A", "B", "D", "C", "E"), ids(new PriceDescendingStrategy().rank(ALL, null)));
    }

    @Test
    void valuePerSqFtLowestFirstUnknownSizeLast() {
        // A 450/sqft, D 500, C 625, B 700, E unknown
        assertEquals(List.of("A", "D", "C", "B", "E"), ids(new ValuePerSqFtStrategy().rank(ALL, null)));
    }

    @Test
    void severalUnknownSizesSortLastByIdNotAsZero() {
        Listing f = listing("F", 1, 1, Listing.UNKNOWN_SQUARE_FEET, PropertyType.CONDO, "X");
        List<Listing> ranked = new ValuePerSqFtStrategy().rank(List.of(f, E, C), null);
        assertEquals(List.of("C", "E", "F"), ids(ranked));
    }

    @Test
    void rankIsIndependentOfInputOrder() {
        List<Listing> shuffled = new ArrayList<>(ALL);
        Collections.reverse(shuffled);
        for (RankingStrategy s : List.of(new PriceAscendingStrategy(), new PriceDescendingStrategy(),
                new ValuePerSqFtStrategy(), new BestMatchStrategy())) {
            assertEquals(ids(s.rank(ALL, null)), ids(s.rank(shuffled, null)), s.getName());
        }
    }

    @Test
    void rankDoesNotModifyInput() {
        List<Listing> input = new ArrayList<>(List.of(A, C));
        new PriceAscendingStrategy().rank(input, null);
        assertEquals(List.of("A", "C"), ids(input));
    }

    @Test
    void emptyListRanksToEmpty() {
        assertTrue(new BestMatchStrategy().rank(List.of(), SearchCriteria.empty()).isEmpty());
    }

    @Test
    void strategiesHaveDistinctNames() {
        assertEquals(4, java.util.Set.of(new PriceAscendingStrategy().getName(),
                new PriceDescendingStrategy().getName(), new ValuePerSqFtStrategy().getName(),
                new BestMatchStrategy().getName()).size());
    }

    // ---- best match ----

    @Test
    void bestMatchWithNoCriteriaFallsBackToIdOrder() {
        BestMatchStrategy s = new BestMatchStrategy();
        assertEquals(0, s.score(A, null), 1e-9);
        assertEquals(List.of("A", "B", "C", "D", "E"), ids(s.rank(ALL, SearchCriteria.empty())));
    }

    @Test
    void bestMatchFullyMatchingListingScoresOne() {
        SearchCriteria c = new SearchCriteriaBuilder().withMaxPrice(750_000.0).withMinBedrooms(3)
                .withType(PropertyType.HOUSE).withFeature("garage").build();
        assertEquals(1.0, new BestMatchStrategy().score(D, c), 1e-9);
    }

    @Test
    void bestMatchGivesPartialCreditForNearMisses() {
        SearchCriteria c = new SearchCriteriaBuilder().withMaxPrice(750_000.0).withMinBedrooms(3)
                .withType(PropertyType.HOUSE).withFeature("garage").build();
        // A: price 900k vs 750k max -> 0.8; beds ok 1; house 1; garage 1 -> 3.8 / 4
        assertEquals(0.95, new BestMatchStrategy().score(A, c), 1e-9);
        // C: price 1; beds 2/3; condo 0; no garage 0 -> 1.6667 / 4
        assertEquals((1 + 2.0 / 3) / 4, new BestMatchStrategy().score(C, c), 1e-9);
    }

    @Test
    void bestMatchOrdersByFitThenId() {
        SearchCriteria c = new SearchCriteriaBuilder().withMaxPrice(750_000.0).withMinBedrooms(3)
                .withType(PropertyType.HOUSE).withFeature("garage").build();
        // D 1.0, A 0.95, B (1+1+0+0)/4 = 0.5, C 0.4167, E (1 + 1/3)/4 = 0.333
        assertEquals(List.of("D", "A", "B", "C", "E"), ids(new BestMatchStrategy().rank(ALL, c)));
    }

    @Test
    void bestMatchCountsFractionOfRequiredFeatures() {
        SearchCriteria c = new SearchCriteriaBuilder().withFeature("garage").withFeature("pool")
                .withFeature("gym").build();
        assertEquals(2.0 / 3, new BestMatchStrategy().score(D, c), 1e-9);
    }

    @Test
    void bestMatchNeighborhoodIsCaseInsensitive() {
        SearchCriteria c = new SearchCriteriaBuilder().withNeighborhood("MAPLE").build();
        assertEquals(1.0, new BestMatchStrategy().score(B, c), 1e-9);
        assertEquals(0.0, new BestMatchStrategy().score(C, c), 1e-9);
    }

    @Test
    void priceFitDecaysBelowMinimumAndFloorsAtZero() {
        assertEquals(1.0, BestMatchStrategy.priceFit(500, 400.0, 600.0), 1e-9);
        assertEquals(0.5, BestMatchStrategy.priceFit(200, 400.0, null), 1e-9);
        assertEquals(0.0, BestMatchStrategy.priceFit(5_000, null, 1_000.0), 1e-9);
        assertEquals(0.0, BestMatchStrategy.priceFit(5, null, 0.0), 1e-9);
    }

    @Test
    void minimumFitHandlesZeroMinimum() {
        assertEquals(1.0, BestMatchStrategy.minimumFit(0, 0), 1e-9);
        assertEquals(0.0, BestMatchStrategy.minimumFit(0, 2), 1e-9);
    }
}
