package ca.yorku.realestate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.agent.AgentResponse;
import ca.yorku.realestate.agent.llm.MockLLMClient;
import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.domain.SearchCriteriaBuilder;
import ca.yorku.realestate.domain.finance.FinancialProfile;
import ca.yorku.realestate.domain.ranking.PriceAscendingStrategy;
import ca.yorku.realestate.persistence.PreferenceStore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The facade wired to the agent, with a mock model. */
class RealEstateServiceAgentTest {

    private static final String DECIDE = "Decide which tool";
    private static final String EXTRACT = "Extract home search criteria";
    private static final String EXPLAIN = "Explain these results";

    private final MockLLMClient llm = new MockLLMClient();

    private RealEstateService service() throws Exception {
        Path prefs = Files.createTempDirectory("svc-agent");
        prefs.toFile().deleteOnExit();
        RealEstateService s = RealEstateService.create(new PreferenceStore(prefs), llm);
        s.loadListings(new File(getClass().getResource("/data/listings.json").toURI()));
        return s;
    }

    @Test
    void agentSearchUpdatesSessionCriteriaAndNotifiesObservers() throws Exception {
        RealEstateService s = service();
        List<SearchCriteria> seen = new ArrayList<>();
        s.addObserver(new SearchObserver() {
            public void onResultsChanged(List<Listing> results) {
            }

            public void onCriteriaChanged(SearchCriteria c) {
                seen.add(c);
            }
        });
        llm.setResponse(DECIDE, "{\"tool\": \"search\"}");
        llm.setResponse(EXTRACT, "{\"minBedrooms\": 4, \"types\": [\"house\"]}");
        llm.setResponse(EXPLAIN, "These are the larger houses.");

        AgentResponse r = s.askAgent("4 bedroom house");

        assertEquals(Integer.valueOf(4), s.getCriteria().getMinBedrooms());
        assertEquals(r.getListings(), s.getResults());
        assertEquals(s.getCriteria(), seen.get(seen.size() - 1), "criteria panel sees the agent's interpretation");
    }

    @Test
    void manualPanelEditCarriesIntoNextChatMessage() throws Exception {
        RealEstateService s = service();
        s.search(new SearchCriteriaBuilder().withMaxPrice(900_000.0).build());
        llm.setResponse(DECIDE, "{\"tool\": \"search\"}");
        llm.setResponse(EXTRACT, "{\"minBedrooms\": 3}");
        llm.setResponse(EXPLAIN, "Within budget.");

        s.askAgent("3 bedrooms please");

        assertEquals(900_000.0, s.getCriteria().getMaxPrice(), "panel budget kept");
        assertEquals(Integer.valueOf(3), s.getCriteria().getMinBedrooms());
        assertTrue(s.getResults().stream().allMatch(l -> l.getPrice() <= 900_000 && l.getBedrooms() >= 3));
    }

    @Test
    void extractorIsGivenTheDatasetVocabulary() throws Exception {
        RealEstateService s = service();
        llm.setResponse(DECIDE, "{\"tool\": \"search\"}");
        llm.setResponse(EXTRACT, "{\"minBedrooms\": 1}");
        llm.setResponse(EXPLAIN, "Ok.");
        s.askAgent("one bedroom");
        String extraction = llm.getPrompts().stream().filter(p -> p.contains(EXTRACT)).findFirst().orElseThrow();
        assertTrue(extraction.contains("Liberty Village"));
        assertTrue(extraction.contains("in-suite laundry"));
    }

    @Test
    void sessionProfileAndRankingReachTheAgent() throws Exception {
        RealEstateService s = service();
        s.setFinancialProfile(new FinancialProfile(400_000, 0, 500_000));
        s.setRankingStrategy(new PriceAscendingStrategy());
        llm.setResponse(DECIDE, "{\"tool\": \"mortgage\", \"listingIds\": [\"L002\"]}");
        AgentResponse mortgage = s.askAgent("can I afford L002?");
        assertTrue(mortgage.getAffordability().isPresent());

        llm.setResponse(DECIDE, "{\"tool\": \"search\"}");
        llm.setResponse(EXTRACT, "{\"types\": [\"condo\"]}");
        llm.setResponse(EXPLAIN, "Cheapest first.");
        List<Listing> r = s.askAgent("condos").getListings();
        for (int i = 1; i < r.size(); i++) {
            assertTrue(r.get(i - 1).getPrice() <= r.get(i).getPrice());
        }
    }

    @Test
    void nonSearchAnswerLeavesResultsAlone() throws Exception {
        RealEstateService s = service();
        List<Listing> before = s.getResults();
        llm.setResponse(DECIDE, "{\"tool\": \"mortgage\", \"listingIds\": [\"L001\"]}");
        s.askAgent("payment on L001");
        assertEquals(before, s.getResults());
    }

    @Test
    void compareWorksWithoutTheModel() throws Exception {
        RealEstateService s = service();
        llm.setAvailable(false);
        AgentResponse r = s.compare(List.of("L001", "L002"));
        assertEquals(2, r.getComparison().orElseThrow().getListings().size());
        assertFalse(r.isAiAvailable());
    }

    @Test
    void serviceWithoutAgentReportsUnavailable() throws IOException {
        Path prefs = Files.createTempDirectory("svc-noagent");
        prefs.toFile().deleteOnExit();
        RealEstateService s = RealEstateService.create(new PreferenceStore(prefs));
        assertFalse(s.hasAgent());
        assertFalse(s.askAgent("hi").isAiAvailable());
        assertFalse(s.compare(List.of("L001", "L002")).isAiAvailable());
    }
}
