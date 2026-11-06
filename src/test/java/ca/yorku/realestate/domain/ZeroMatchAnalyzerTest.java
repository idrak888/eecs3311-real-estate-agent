package ca.yorku.realestate.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.testsupport.TestData;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZeroMatchAnalyzerTest {

    private final List<Listing> all = TestData.bundledRepository().findAll();

    /** The Oct 31 live query, as hard requirements: matches 0 of 60. */
    private static SearchCriteria vaughanQuery() {
        return new SearchCriteriaBuilder().withMaxPrice(1_500_000.0).withMinBedrooms(3)
                .withType(PropertyType.HOUSE).withNeighborhood("Vaughan").withNeighborhood("Woodbridge")
                .withFeature("garage").build();
    }

    @Test
    void namesTheRequirementWhoseRemovalFindsMostMatches() {
        ZeroMatchAnalyzer.Diagnosis d = ZeroMatchAnalyzer.analyze(vaughanQuery(), all);
        List<String> order = d.relaxations().stream().map(ZeroMatchAnalyzer.Relaxation::requirement).toList();
        // Counts verified independently against listings.json: area 3, garage 3, price 1, beds 1, type 1.
        assertEquals("the area Vaughan or Woodbridge", order.get(0));
        assertEquals(3, d.relaxations().get(0).matches());
        assertEquals("the feature \"garage\"", order.get(1));
        assertEquals(3, d.relaxations().get(1).matches());
        assertEquals(List.of(1, 1, 1), d.relaxations().subList(2, 5).stream().map(ZeroMatchAnalyzer.Relaxation::matches).toList());
    }

    @Test
    void relaxedCriteriaReallyFindThatManyListings() {
        for (ZeroMatchAnalyzer.Relaxation r : ZeroMatchAnalyzer.analyze(vaughanQuery(), all).relaxations()) {
            long actual = all.stream().filter(r.relaxed().toFilter()::matches).count();
            assertEquals(r.matches(), (int) actual, r.requirement());
        }
    }

    @Test
    void messageIsReadableAndListsAlternatives() {
        String m = ZeroMatchAnalyzer.analyze(vaughanQuery(), all).message();
        assertEquals("The most restrictive requirement is the area Vaughan or Woodbridge: without it, 3 listings match."
                + " Also worth relaxing: the feature \"garage\" (3), a maximum price of $1,500,000 (1).", m);
    }

    @Test
    void eachFeatureIsTriedSeparately() {
        SearchCriteria c = new SearchCriteriaBuilder().withFeature("garage").withFeature("pool")
                .withFeature("concierge").build();
        List<String> labels = ZeroMatchAnalyzer.analyze(c, all).relaxations().stream()
                .map(ZeroMatchAnalyzer.Relaxation::requirement).toList();
        assertTrue(labels.contains("the feature \"pool\""));
        assertTrue(labels.contains("the feature \"concierge\""));
        assertEquals(3, labels.size());
    }

    @Test
    void whenNoSingleRemovalHelpsSaySo() {
        SearchCriteria c = new SearchCriteriaBuilder().withMaxPrice(1.0).withMinBedrooms(50).build();
        ZeroMatchAnalyzer.Diagnosis d = ZeroMatchAnalyzer.analyze(c, all);
        assertTrue(d.mostRestrictive().isEmpty());
        assertTrue(d.message().startsWith("No single change finds a match"));
    }

    @Test
    void singularWordingAndMinimumBounds() {
        Listing only = TestData.listing("X1", 500_000, 2, 1, 900, PropertyType.CONDO, "Downtown", 2015);
        SearchCriteria c = new SearchCriteriaBuilder().withMinPrice(600_000.0).withMinBathrooms(1).build();
        String m = ZeroMatchAnalyzer.analyze(c, List.of(only)).message();
        assertEquals("The most restrictive requirement is a minimum price of $600,000: without it, 1 listing matches.", m);
    }

    @Test
    void noListingsAndInvalidCriteria() {
        assertEquals("No listings were loaded.", ZeroMatchAnalyzer.analyze(vaughanQuery(), List.of()).message());
        SearchCriteria bad = new SearchCriteriaBuilder().withMinPrice(5.0).withMaxPrice(1.0).build();
        assertThrows(IllegalArgumentException.class, () -> ZeroMatchAnalyzer.analyze(bad, all));
    }
}
