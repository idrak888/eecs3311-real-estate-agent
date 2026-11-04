package ca.yorku.realestate.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.domain.SearchCriteriaBuilder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemoryManagerTest {

    private static Listing listing(String id) {
        return new Listing(id, 500_000, 2, 1, 900, PropertyType.CONDO, "Downtown", 2015, null);
    }

    @Test
    void budgetFromFirstTurnPersistsAcrossLaterTurns() {
        MemoryManager m = new MemoryManager();
        m.mergeCriteria(new SearchCriteriaBuilder().withMaxPrice(850_000.0).build());
        m.mergeCriteria(new SearchCriteriaBuilder().withMinBedrooms(3).build());
        m.mergeCriteria(new SearchCriteriaBuilder().withType(PropertyType.HOUSE).build());
        SearchCriteria c = m.getCriteria();
        assertEquals(850_000.0, c.getMaxPrice());
        assertEquals(Integer.valueOf(3), c.getMinBedrooms());
        assertEquals(Set.of(PropertyType.HOUSE), c.getTypes());
    }

    @Test
    void laterValueOverridesEarlier() {
        MemoryManager m = new MemoryManager();
        m.mergeCriteria(new SearchCriteriaBuilder().withMaxPrice(850_000.0).build());
        assertEquals(900_000.0, m.mergeCriteria(new SearchCriteriaBuilder().withMaxPrice(900_000.0).build()).getMaxPrice());
    }

    @Test
    void setCriteriaReplacesAndNullClears() {
        MemoryManager m = new MemoryManager();
        m.mergeCriteria(new SearchCriteriaBuilder().withMinBedrooms(3).build());
        m.setCriteria(new SearchCriteriaBuilder().withMaxPrice(1.0).build());
        assertEquals(null, m.getCriteria().getMinBedrooms());
        m.setCriteria(null);
        assertTrue(m.getCriteria().isEmpty());
    }

    @Test
    void preferencesAreSeparateFromCriteriaAndHardCriteriaWinInRanking() {
        MemoryManager m = new MemoryManager();
        m.mergeCriteria(new SearchCriteriaBuilder().withMaxPrice(800_000.0).build());
        m.mergePreferences(new SearchCriteriaBuilder().withNeighborhood("Vaughan").withMaxPrice(700_000.0).build());
        assertTrue(m.getCriteria().getNeighborhoods().isEmpty(), "preferences never filter");
        SearchCriteria ranking = m.getRankingCriteria();
        assertEquals(Set.of("Vaughan"), ranking.getNeighborhoods());
        assertEquals(800_000.0, ranking.getMaxPrice(), "hard criterion wins on conflict");
    }

    @Test
    void historyIsCappedAtMaxTurns() {
        MemoryManager m = new MemoryManager();
        for (int i = 0; i < MemoryManager.MAX_TURNS + 5; i++) {
            m.addTurn(new ConversationTurn("q" + i, "a" + i));
        }
        List<ConversationTurn> h = m.getHistory();
        assertEquals(MemoryManager.MAX_TURNS, h.size());
        assertEquals("q5", h.get(0).getUserMessage());
    }

    @Test
    void contextIncludesCriteriaResultsWithPositionsAndRecentTurns() {
        MemoryManager m = new MemoryManager();
        m.mergeCriteria(new SearchCriteriaBuilder().withMinBedrooms(2).build());
        m.setLastResults(List.of(listing("L001"), listing("L002")));
        m.addTurn(new ConversationTurn("old question", "old answer"));
        m.addTurn(new ConversationTurn("2 bed condo", "Found 2."));
        String ctx = m.getContext(1);
        assertTrue(ctx.contains("bedrooms >= 2"));
        assertTrue(ctx.contains("1. L001"));
        assertTrue(ctx.contains("2. L002"));
        assertTrue(ctx.contains("User: 2 bed condo"));
        assertFalse(ctx.contains("old question"), "only the requested number of turns");
    }

    @Test
    void contextWhenEmptySaysNoCriteria() {
        String ctx = new MemoryManager().getContext(5);
        assertTrue(ctx.contains("Active search criteria: none"));
        assertFalse(ctx.contains("Recent conversation"));
        assertFalse(ctx.contains("Soft preferences"));
    }

    @Test
    void contextLimitsResultsAndTruncatesLongReplies() {
        MemoryManager m = new MemoryManager();
        List<Listing> many = new ArrayList<>();
        for (int i = 1; i <= 14; i++) {
            many.add(listing(String.format("L%03d", i)));
        }
        m.setLastResults(many);
        m.addTurn(new ConversationTurn("q", "x".repeat(1000)));
        String ctx = m.getContext(5);
        assertTrue(ctx.contains("10. L010"));
        assertFalse(ctx.contains("11. L011"));
        assertTrue(ctx.contains("(4 more not shown)"));
        assertTrue(ctx.contains("x".repeat(MemoryManager.MAX_QUOTED_REPLY) + "..."));
        assertFalse(ctx.contains("x".repeat(MemoryManager.MAX_QUOTED_REPLY + 1)));
    }

    @Test
    void lastResultsAreACopy() {
        MemoryManager m = new MemoryManager();
        List<Listing> input = new ArrayList<>(List.of(listing("A")));
        m.setLastResults(input);
        input.add(listing("B"));
        assertEquals(1, m.getLastResults().size());
        m.setLastResults(null);
        assertTrue(m.getLastResults().isEmpty());
    }

    @Test
    void clearForgetsEverything() {
        MemoryManager m = new MemoryManager();
        m.mergeCriteria(new SearchCriteriaBuilder().withMinBedrooms(1).build());
        m.mergePreferences(new SearchCriteriaBuilder().withFeature("pool").build());
        m.setLastResults(List.of(listing("A")));
        m.addTurn(new ConversationTurn("q", "a"));
        m.clear();
        assertTrue(m.getCriteria().isEmpty());
        assertTrue(m.getPreferences().isEmpty());
        assertTrue(m.getLastResults().isEmpty());
        assertTrue(m.getHistory().isEmpty());
    }

    @Test
    void turnDefaultsAndValidation() {
        ConversationTurn t = new ConversationTurn("hi", null, null);
        assertEquals("", t.getAgentResponse());
        assertTrue(!t.getTimestamp().isAfter(Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new ConversationTurn(null, "a"));
        assertThrows(IllegalArgumentException.class, () -> new MemoryManager().addTurn(null));
    }
}
