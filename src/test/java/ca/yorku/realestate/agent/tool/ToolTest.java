package ca.yorku.realestate.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.domain.SearchCriteriaBuilder;
import ca.yorku.realestate.domain.finance.AffordabilityResult;
import ca.yorku.realestate.domain.finance.FinancialProfile;
import ca.yorku.realestate.domain.finance.MortgageCalculator;
import ca.yorku.realestate.domain.finance.MortgageResult;
import ca.yorku.realestate.domain.ranking.PriceAscendingStrategy;
import ca.yorku.realestate.persistence.ListingRepository;
import ca.yorku.realestate.testsupport.TestData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolTest {

    private final ListingRepository repo = TestData.bundledRepository();

    private ToolManager manager() {
        ToolManager m = new ToolManager();
        m.register(new SearchTool(repo));
        m.register(new MortgageTool(repo, new MortgageCalculator()));
        return m;
    }

    // ---- ToolRequest ----

    @Test
    void requestTypedGettersAcceptNumbersAndNumericStrings() {
        ToolRequest r = ToolRequest.of(Map.of("a", 5, "b", "1,250,000", "c", "4", "d", "x"));
        assertEquals(5.0, r.getDouble("a").orElseThrow(), 1e-9);
        assertEquals(1_250_000.0, r.getDouble("b").orElseThrow(), 1e-9);
        assertEquals(Integer.valueOf(4), r.getInt("c").orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> r.getDouble("d"));
        assertThrows(IllegalArgumentException.class, () -> ToolRequest.of(Map.of("y", 2.5)).getInt("y"));
        assertTrue(r.getDouble("missing").isEmpty());
    }

    @Test
    void requestStringListAndWith() {
        ToolRequest r = ToolRequest.empty().with("ids", List.of("L1", " ", "L2")).with("one", "L3");
        assertEquals(List.of("L1", "L2"), r.getStringList("ids"));
        assertEquals(List.of("L3"), r.getStringList("one"));
        assertTrue(r.getStringList("none").isEmpty());
        assertFalse(r.with("one", null).has("one"));
        assertTrue(r.getString("blank").isEmpty());
    }

    // ---- ToolManager ----

    @Test
    void managerListsDescribesAndRejectsDuplicates() {
        ToolManager m = manager();
        assertEquals(List.of("search", "mortgage"), m.listAvailable());
        assertTrue(m.describeTools().contains("- mortgage: monthly payment"));
        assertThrows(IllegalArgumentException.class, () -> m.register(new SearchTool(repo)));
    }

    @Test
    void unknownToolAndToolExceptionsBecomeFailures() {
        ToolManager m = manager();
        assertEquals("Unknown tool: teleport", m.execute("teleport", ToolRequest.empty()).getErrorMessage());
        m.register(new AgentTool() {
            public String getName() {
                return "broken";
            }

            public String getDescription() {
                return "always fails";
            }

            public ToolResult execute(ToolRequest r) {
                throw new IllegalStateException("boom");
            }
        });
        ToolResult r = m.execute("broken", null);
        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage().contains("boom"));
    }

    // ---- SearchTool ----

    @Test
    void searchReturnsRankedMatchesWithCounts() {
        SearchCriteria c = new SearchCriteriaBuilder().withMaxPrice(1_000_000.0).withMinBedrooms(2)
                .withType(PropertyType.CONDO).withType(PropertyType.TOWNHOUSE).build();
        ToolResult r = manager().execute("search", ToolRequest.empty().with(SearchTool.CRITERIA, c)
                .with(SearchTool.STRATEGY, new PriceAscendingStrategy()));
        assertTrue(r.isSuccess());
        assertEquals(8, r.getListings().size());
        assertEquals(Integer.valueOf(8), r.getData(SearchTool.COUNT, Integer.class).orElseThrow());
        assertEquals(Integer.valueOf(60), r.getData(SearchTool.TOTAL, Integer.class).orElseThrow());
        List<Listing> l = r.getListings();
        for (int i = 1; i < l.size(); i++) {
            assertTrue(l.get(i - 1).getPrice() <= l.get(i).getPrice());
        }
    }

    @Test
    void searchRanksByPreferencesWithoutFilteringOnThem() {
        SearchCriteria hard = new SearchCriteriaBuilder().withType(PropertyType.HOUSE).build();
        SearchCriteria withPrefs = SearchCriteriaBuilder.from(hard).withNeighborhood("Vaughan").build();
        ToolResult r = manager().execute("search", ToolRequest.empty()
                .with(SearchTool.CRITERIA, hard).with(SearchTool.RANKING_CRITERIA, withPrefs));
        int houses = (int) repo.findAll().stream().filter(x -> x.getType() == PropertyType.HOUSE).count();
        assertEquals(houses, r.getListings().size(), "preference did not filter");
        assertEquals("Vaughan", r.getListings().get(0).getNeighborhood(), "preference ranked first");
    }

    @Test
    void searchWithoutOrWithInvalidCriteriaFails() {
        assertFalse(manager().execute("search", ToolRequest.empty()).isSuccess());
        SearchCriteria bad = new SearchCriteriaBuilder().withMinPrice(5.0).withMaxPrice(1.0).build();
        ToolResult r = manager().execute("search", ToolRequest.empty().with(SearchTool.CRITERIA, bad));
        assertTrue(r.getErrorMessage().contains("exceeds maximum"));
    }

    @Test
    void searchZeroMatchesIsSuccessWithEmptyList() {
        SearchCriteria none = new SearchCriteriaBuilder().withMaxPrice(1.0).build();
        ToolResult r = manager().execute("search", ToolRequest.empty().with(SearchTool.CRITERIA, none));
        assertTrue(r.isSuccess());
        assertTrue(r.getListings().isEmpty());
    }

    // ---- MortgageTool ----

    @Test
    void mortgageUsesDefaultsAndReturnsTheListing() {
        ToolResult r = manager().execute("mortgage", ToolRequest.empty().with(MortgageTool.LISTING_ID, "L001"));
        assertTrue(r.isSuccess());
        Listing l = r.getListings().get(0);
        MortgageResult m = r.getData(MortgageTool.MORTGAGE, MortgageResult.class).orElseThrow();
        assertEquals(l.getPrice() * 0.8, m.getPrincipal(), 1e-6);
        assertTrue(r.getData(MortgageTool.AFFORDABILITY, AffordabilityResult.class).isEmpty());
    }

    @Test
    void mortgageAcceptsStringTermsFromModelJson() {
        ToolResult r = manager().execute("mortgage", ToolRequest.of(Map.of(
                MortgageTool.LISTING_ID, "L001", MortgageTool.DOWN_PAYMENT, "200000",
                MortgageTool.RATE, 4.8, MortgageTool.YEARS, "30")));
        MortgageResult m = r.getData(MortgageTool.MORTGAGE, MortgageResult.class).orElseThrow();
        Listing l = repo.findById("L001").orElseThrow();
        assertEquals(l.getPrice() - 200_000, m.getPrincipal(), 1e-6);
        double expected = new MortgageCalculator().monthlyPayment(l.getPrice(),
                new ca.yorku.realestate.domain.finance.MortgageTerms(200_000, 4.8, 30));
        assertEquals(expected, m.getMonthlyPayment(), 1e-9);
    }

    @Test
    void mortgageWithProfileAddsAffordabilityAndUsesProfileDownPayment() {
        FinancialProfile p = new FinancialProfile(200_000, 0, 300_000);
        ToolResult r = manager().execute("mortgage", ToolRequest.empty()
                .with(MortgageTool.LISTING_ID, "L001").with(MortgageTool.PROFILE, p));
        assertTrue(r.getData(MortgageTool.AFFORDABILITY, AffordabilityResult.class).isPresent());
        Listing l = r.getListings().get(0);
        assertEquals(l.getPrice() - 300_000,
                r.getData(MortgageTool.MORTGAGE, MortgageResult.class).orElseThrow().getPrincipal(), 1e-6);
    }

    @Test
    void mortgageErrorsAreReadableFailures() {
        ToolManager m = manager();
        assertTrue(m.execute("mortgage", ToolRequest.empty()).getErrorMessage().contains("listing ID is needed"));
        assertEquals("No listing with ID L999",
                m.execute("mortgage", ToolRequest.empty().with(MortgageTool.LISTING_ID, "L999")).getErrorMessage());
        ToolResult badRate = m.execute("mortgage", ToolRequest.empty()
                .with(MortgageTool.LISTING_ID, "L001").with(MortgageTool.RATE, 480));
        assertFalse(badRate.isSuccess());
        assertTrue(badRate.getErrorMessage().contains("Annual rate"));
    }

    @Test
    void resultIsImmutableAndFailureHasDefaultMessage() {
        ToolResult r = ToolResult.success(List.of(), Map.of("k", 1));
        assertThrows(UnsupportedOperationException.class, () -> r.getData().put("x", 2));
        assertEquals("Tool failed", ToolResult.failure(null).getErrorMessage());
        assertEquals(null, r.getErrorMessage());
    }
}
