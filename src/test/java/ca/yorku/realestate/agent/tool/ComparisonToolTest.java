package ca.yorku.realestate.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import ca.yorku.realestate.domain.finance.MortgageCalculator;
import ca.yorku.realestate.domain.finance.MortgageTerms;
import ca.yorku.realestate.persistence.ListingRepository;
import ca.yorku.realestate.testsupport.TestData;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComparisonToolTest {

    // A: cheap, small, old.  B: pricey, big, new.  C: unknown size, same beds as B.
    private static final Listing A = TestData.listing("A", 600_000, 2, 1, 900, PropertyType.CONDO, "Downtown", 1990, "gym");
    private static final Listing B = TestData.listing("B", 900_000, 4, 3, 2000, PropertyType.HOUSE, "Maple", 2020, "garage", "pool");
    private static final Listing C = TestData.listing("C", 750_000, 4, 2, Listing.UNKNOWN_SQUARE_FEET, PropertyType.TOWNHOUSE, "Vaughan", 2010);
    private static final Listing D = TestData.listing("D", 650_000, 3, 2, 1200, PropertyType.CONDO, "Downtown", 2015);
    private static final Listing E = TestData.listing("E", 700_000, 3, 2, 1300, PropertyType.CONDO, "Downtown", 2016);

    private final ListingRepository repo = TestData.repositoryOf(A, B, C, D, E);
    private final ComparisonTool tool = new ComparisonTool(repo, new MortgageCalculator(), 2026);

    private ComparisonResult compare(String... ids) {
        ToolResult r = tool.execute(ToolRequest.empty().with(ComparisonTool.LISTING_IDS, List.of(ids)));
        assertTrue(r.isSuccess(), r.getErrorMessage() == null ? "" : r.getErrorMessage());
        return r.getData(ComparisonTool.COMPARISON, ComparisonResult.class).orElseThrow();
    }

    @Test
    void matrixHasAllF08AttributesInOrder() {
        ComparisonResult c = compare("A", "B");
        assertEquals(List.of("Price", "Square feet", "Bedrooms", "Bathrooms", "Age (years)",
                "Price per sq ft", "Monthly payment", "Type", "Neighborhood", "Features"), c.getAttributeNames());
        assertEquals(List.of(600_000.0, 900_000.0), c.getAttribute("Price"));
        assertEquals(List.of(36.0, 6.0), c.getAttribute("Age (years)"));
        assertEquals(List.of("condo", "house"), c.getAttribute("Type"));
    }

    @Test
    void leadersRespectHigherOrLowerIsBetter() {
        ComparisonResult c = compare("A", "B");
        assertEquals("A", c.getLeader("Price"));
        assertEquals("B", c.getLeader("Square feet"));
        assertEquals("B", c.getLeader("Bedrooms"));
        assertEquals("B", c.getLeader("Age (years)"), "newer is better");
        assertEquals("B", c.getLeader("Price per sq ft"), "450/sqft beats 667/sqft");
        assertEquals("A", c.getLeader("Monthly payment"));
        assertNull(c.getLeader("Type"), "non-numeric rows have no leader");
    }

    @Test
    void monthlyPaymentUsesDefaultTerms() {
        double expected = new MortgageCalculator().monthlyPayment(600_000, MortgageTerms.defaultsFor(600_000));
        assertEquals(expected, (Double) compare("A", "B").getAttribute("Monthly payment").get(0), 1e-9);
    }

    @Test
    void missingAttributeIsUnavailableAndExcludedFromLeader() {
        ComparisonResult c = compare("B", "C");
        assertNull(c.getAttribute("Square feet").get(1));
        assertNull(c.getLeader("Square feet"), "only one value available, so no leader");
        assertNull(c.getAttribute("Price per sq ft").get(1));
        assertEquals("unavailable", ComparisonResult.format("Square feet", null));
    }

    @Test
    void tieForBestHasNoLeader() {
        assertNull(compare("B", "C").getLeader("Bedrooms"), "both have 4");
        assertEquals("B", compare("B", "C").getLeader("Bathrooms"));
    }

    @Test
    void fewerThanTwoIsUnavailable() {
        for (List<String> ids : List.of(List.<String>of(), List.of("A"), List.of("A", "A"))) {
            ToolResult r = tool.execute(ToolRequest.empty().with(ComparisonTool.LISTING_IDS, ids));
            assertFalse(r.isSuccess());
            assertTrue(r.getErrorMessage().contains("at least two"));
        }
    }

    @Test
    void moreThanFourIsCappedAndFlagged() {
        ToolResult r = tool.execute(ToolRequest.empty()
                .with(ComparisonTool.LISTING_IDS, List.of("A", "B", "C", "D", "E")));
        assertTrue(r.isSuccess());
        assertEquals(4, r.getListings().size());
        assertEquals(Boolean.TRUE, r.getData(ComparisonTool.CAPPED, Boolean.class).orElseThrow());
        assertEquals(List.of("A", "B", "C", "D"), r.getListings().stream().map(Listing::getId).toList());
    }

    @Test
    void unknownIdFails() {
        ToolResult r = tool.execute(ToolRequest.empty().with(ComparisonTool.LISTING_IDS, List.of("A", "ZZZ")));
        assertEquals("No listing with ID ZZZ", r.getErrorMessage());
    }

    @Test
    void tableMarksLeadersAndFormatsValues() {
        String table = compare("A", "B").toTable();
        assertTrue(table.contains("$600,000 *"), table);
        assertTrue(table.contains("garage, pool"));
        assertTrue(table.contains("* = best in row"));
        assertEquals("none", ComparisonResult.format("Features", List.of()));
        assertEquals("2,000", ComparisonResult.format("Square feet", 2000.0), "whole numbers without decimals");
        assertEquals("1.5", ComparisonResult.format("Rating", 1.5));
    }

    @Test
    void matrixIsUnmodifiableAndUnknownAttributeRejected() {
        ComparisonResult c = compare("A", "B");
        assertThrows(UnsupportedOperationException.class, () -> c.getAttribute("Price").set(0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> c.getAttribute("Colour"));
        assertTrue(c.isNumeric("Price"));
        assertFalse(c.isNumeric("Type"));
    }
}
