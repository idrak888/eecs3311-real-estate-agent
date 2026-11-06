package ca.yorku.realestate.ui.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.domain.SearchCriteriaBuilder;
import ca.yorku.realestate.domain.ranking.PriceDescendingStrategy;
import ca.yorku.realestate.testsupport.TestData;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** GUI logic that runs without a display: criteria conversion and the results table model. */
class GuiModelTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private CriteriaPanel panel() {
        CriteriaPanel p = new CriteriaPanel();
        p.setVocabulary(List.of("Downtown", "Liberty Village", "Maple"), List.of("garage", "gym"));
        return p;
    }

    @Test
    void emptyPanelBuildsEmptyCriteria() {
        assertTrue(panel().buildCriteria().isEmpty());
    }

    @Test
    void controlsRoundTripThroughCriteria() {
        SearchCriteria original = new SearchCriteriaBuilder().withMinPrice(500_000.0).withMaxPrice(850_000.0)
                .withMinBedrooms(3).withMinBathrooms(2).withType(PropertyType.HOUSE).withType(PropertyType.TOWNHOUSE)
                .withNeighborhood("liberty village").withFeature("garage").build();
        CriteriaPanel p = panel();
        p.showCriteria(original);
        SearchCriteria rebuilt = p.buildCriteria();
        assertEquals(500_000.0, rebuilt.getMinPrice());
        assertEquals(850_000.0, rebuilt.getMaxPrice());
        assertEquals(Integer.valueOf(3), rebuilt.getMinBedrooms());
        assertEquals(Set.of(PropertyType.HOUSE, PropertyType.TOWNHOUSE), rebuilt.getTypes());
        assertEquals(Set.of("Liberty Village"), rebuilt.getNeighborhoods(), "matched case-insensitively to the list");
        assertEquals(Set.of("garage"), rebuilt.getRequiredFeatures());
    }

    @Test
    void amountsAcceptShorthand() {
        CriteriaPanel p = panel();
        p.maxPriceField().setText("1.2M");
        p.minPriceField().setText("$500k");
        SearchCriteria c = p.buildCriteria();
        assertEquals(1_200_000.0, c.getMaxPrice());
        assertEquals(500_000.0, c.getMinPrice());
    }

    @Test
    void malformedInputNamesTheField() {
        CriteriaPanel p = panel();
        p.maxPriceField().setText("lots");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, p::buildCriteria);
        assertTrue(e.getMessage().startsWith("Max price"));
        p.maxPriceField().setText("");
        p.minBedsField().setText("2.5");
        assertTrue(assertThrows(IllegalArgumentException.class, p::buildCriteria).getMessage().startsWith("Min bedrooms"));
    }

    @Test
    void invertedRangeBuildsButIsInvalid() {
        CriteriaPanel p = panel();
        p.minPriceField().setText("900000");
        p.maxPriceField().setText("500000");
        assertFalse(p.buildCriteria().isValid(), "the service refuses it with a readable message");
    }

    @Test
    void clearResetsControls() {
        CriteriaPanel p = panel();
        p.showCriteria(new SearchCriteriaBuilder().withMinBedrooms(2).withType(PropertyType.CONDO).build());
        p.clear();
        assertTrue(p.buildCriteria().isEmpty());
        assertFalse(p.typeBox(PropertyType.CONDO).isSelected());
    }

    @Test
    void rankingDropdownOffersAllStrategies() {
        CriteriaPanel p = panel();
        assertEquals("Best match", p.selectedStrategy().getName());
    }

    @Test
    void criteriaChangesReachTheRankingCallback() {
        CriteriaPanel p = panel();
        final String[] chosen = {null};
        p.onRankingChanged(s -> chosen[0] = s.getName());
        // Index 2 is price high to low, matching Formats.strategies() order.
        javax.swing.JComboBox<?> box = findCombo(p);
        box.setSelectedIndex(2);
        assertEquals(new PriceDescendingStrategy().getName(), chosen[0]);
    }

    private static javax.swing.JComboBox<?> findCombo(java.awt.Container c) {
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof javax.swing.JComboBox<?> box) {
                return box;
            }
            if (child instanceof java.awt.Container inner) {
                javax.swing.JComboBox<?> found = findCombo(inner);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void tableModelShowsFormattedRows() {
        Listing known = TestData.listing("A1", 850_000, 3, 2, 1700, PropertyType.HOUSE, "Maple", 2005);
        Listing unknown = TestData.listing("B2", 620_000, 2, 1, Listing.UNKNOWN_SQUARE_FEET, PropertyType.CONDO, "Downtown", 2018);
        ResultsTableModel m = new ResultsTableModel();
        m.setListings(List.of(known, unknown));
        assertEquals(2, m.getRowCount());
        assertEquals(8, m.getColumnCount());
        assertEquals("Price", m.getColumnName(3));
        assertEquals("$850,000", m.getValueAt(0, 3));
        assertEquals("House", m.getValueAt(0, 1));
        assertEquals("$500", m.getValueAt(0, 7));
        assertEquals("unavailable", m.getValueAt(1, 6));
        assertEquals("unavailable", m.getValueAt(1, 7));
        assertEquals("B2", m.getListingAt(1).getId());
        m.setListings(null);
        assertEquals(0, m.getRowCount());
    }

    // ---- chat panel ----

    @Test
    void chatMetaLineSummarizesToolsAndEffects() {
        ca.yorku.realestate.agent.AgentResponse search = ca.yorku.realestate.agent.AgentResponse.builder()
                .tool("search").listings(List.of(TestData.listing("A1", 1, 1, 1, 1, PropertyType.CONDO, "X", 2000)))
                .message("Found 1.").build();
        assertEquals("tools: search · results updated above", ChatPanel.metaLine(search));
        assertEquals("", ChatPanel.metaLine(ca.yorku.realestate.agent.AgentResponse.builder().message("hi").build()));
    }

    @Test
    void chatShowsRepliesNoticesAndDisabledStateWithoutKey() {
        ChatPanel off = new ChatPanel(q -> null, r -> { }, false);
        assertTrue(off.transcriptText().contains("not configured"));
        ChatPanel on = new ChatPanel(q -> null, r -> { }, true);
        on.show(ca.yorku.realestate.agent.AgentResponse.builder().message("Here you go.")
                .notice("Explanation unavailable; showing results only.").build());
        String t = on.transcriptText();
        assertTrue(t.contains("Advisor: Here you go."));
        assertTrue(t.contains("Explanation unavailable"));
    }

    // ---- profile, shortlist, comparison ----

    @Test
    void profileParsingAcceptsShorthandAndBlankDebt() {
        ca.yorku.realestate.domain.finance.FinancialProfile p = ProfileDialog.parse("150k", "", "$160,000");
        assertEquals(150_000, p.getAnnualIncome(), 1e-9);
        assertEquals(0, p.getMonthlyDebt(), 1e-9);
        assertEquals(160_000, p.getDownPayment(), 1e-9);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ProfileDialog.parse("", "0", "1"));
        assertTrue(e.getMessage().startsWith("Annual income"));
        assertThrows(IllegalArgumentException.class, () -> ProfileDialog.parse("1", "-5", "1"));
    }

    @Test
    void shortlistCompareEnabledOnlyForTwoToFour() {
        ShortlistPanel p = new ShortlistPanel();
        List<Listing> five = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            five.add(TestData.listing("S" + i, 100_000 * i, 2, 1, 900, PropertyType.CONDO, "X", 2010));
        }
        p.setListings(five, true);
        assertTrue(p.undoEnabled());
        p.listView().setSelectedIndices(new int[] {0});
        assertFalse(p.compareEnabled());
        p.listView().setSelectedIndices(new int[] {0, 1});
        assertTrue(p.compareEnabled());
        p.listView().setSelectedIndices(new int[] {0, 1, 2, 3});
        assertTrue(p.compareEnabled());
        p.listView().setSelectedIndices(new int[] {0, 1, 2, 3, 4});
        assertFalse(p.compareEnabled(), "more than four is not allowed");
        p.setListings(List.of(), false);
        assertFalse(p.undoEnabled());
    }

    @Test
    void comparisonModelMarksLeadersAndSplitsSummary() {
        ca.yorku.realestate.persistence.ListingRepository repo = TestData.repositoryOf(
                TestData.listing("A", 600_000, 2, 1, 900, PropertyType.CONDO, "X", 1990),
                TestData.listing("B", 900_000, 4, 3, 2000, PropertyType.HOUSE, "Y", 2020));
        ca.yorku.realestate.agent.tool.ToolResult r = new ca.yorku.realestate.agent.tool.ComparisonTool(repo,
                new ca.yorku.realestate.domain.finance.MortgageCalculator(), 2026)
                .execute(ca.yorku.realestate.agent.tool.ToolRequest.empty().with("listingIds", List.of("A", "B")));
        ca.yorku.realestate.agent.tool.ComparisonResult c = r.getData("comparison",
                ca.yorku.realestate.agent.tool.ComparisonResult.class).orElseThrow();
        ComparisonDialog.Model m = new ComparisonDialog.Model(c);
        assertEquals(3, m.getColumnCount());
        assertEquals("A", m.getColumnName(1));
        assertEquals("Price", m.getValueAt(0, 0));
        assertEquals(ComparisonDialog.LEADER_MARK + "$600,000", m.getValueAt(0, 1));
        assertEquals("$900,000", m.getValueAt(0, 2));
        assertTrue(m.isLeader(0, 1));
        assertFalse(m.isLeader(0, 0));

        ca.yorku.realestate.agent.AgentResponse withSummary = ca.yorku.realestate.agent.AgentResponse.builder()
                .message("Comparing A, B.\n\nA is cheaper; B is bigger.").comparison(c).build();
        assertEquals("A is cheaper; B is bigger.", ComparisonDialog.summaryText(withSummary));
        assertEquals("", ComparisonDialog.summaryText(ca.yorku.realestate.agent.AgentResponse.builder()
                .message("Comparing A, B.").comparison(c).build()));
    }

    @Test
    void verdictColorsDiffer() {
        assertFalse(ListingDetailDialog.color(ca.yorku.realestate.domain.finance.Verdict.AFFORDABLE)
                .equals(ListingDetailDialog.color(ca.yorku.realestate.domain.finance.Verdict.UNAFFORDABLE)));
    }

    @Test
    void statusBarDescribesAssistantState() {
        assertTrue(MainWindow.aiStatus(false, false).startsWith("not configured"));
        assertEquals("ready", MainWindow.aiStatus(true, false));
        assertTrue(MainWindow.aiStatus(true, true).startsWith("unavailable"));
    }
}
