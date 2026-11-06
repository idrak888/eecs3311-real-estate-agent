package ca.yorku.realestate.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.agent.llm.LLMClient;
import ca.yorku.realestate.agent.llm.LLMException;
import ca.yorku.realestate.agent.tool.ComparisonTool;
import ca.yorku.realestate.agent.tool.MortgageTool;
import ca.yorku.realestate.agent.tool.SearchTool;
import ca.yorku.realestate.agent.tool.ToolManager;
import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.finance.FinancialProfile;
import ca.yorku.realestate.domain.finance.MortgageCalculator;
import ca.yorku.realestate.domain.finance.MortgageTerms;
import ca.yorku.realestate.persistence.ListingRepository;
import ca.yorku.realestate.testsupport.TestData;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class AdvisorAgentTest {

    /**
     * Scripted model: replies are queued per prompt kind (decision, extraction, explanation),
     * so each test states exactly what the model "says" at each step.
     */
    static class ScriptedLLM implements LLMClient {
        final LinkedList<String> decisions = new LinkedList<>();
        final LinkedList<String> extractions = new LinkedList<>();
        final LinkedList<String> explanations = new LinkedList<>();
        final List<String> prompts = new ArrayList<>();
        boolean available = true;
        boolean failDecision;

        @Override
        public String complete(String prompt) {
            prompts.add(prompt);
            if (prompt.contains(AdvisorAgent.EXPLAIN_MARKER)) {
                return next(explanations, "explanation");
            }
            throw new AssertionError("unexpected free-text prompt");
        }

        @Override
        public String completeStructured(String prompt, String schema) {
            prompts.add(prompt);
            if (prompt.contains(AdvisorAgent.DECISION_MARKER)) {
                if (failDecision) {
                    throw new LLMException("HTTP 529: overloaded");
                }
                return next(decisions, "decision");
            }
            return next(extractions, "extraction");
        }

        private static String next(LinkedList<String> q, String kind) {
            if (q.isEmpty()) {
                throw new AssertionError("no scripted " + kind + " reply left");
            }
            return q.removeFirst();
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        long count(String marker) {
            return prompts.stream().filter(p -> p.contains(marker)).count();
        }

        String last(String marker) {
            for (int i = prompts.size() - 1; i >= 0; i--) {
                if (prompts.get(i).contains(marker)) {
                    return prompts.get(i);
                }
            }
            return null;
        }
    }

    private static final String SEARCH = "{\"tool\": \"search\"}";
    private static final String CONDO_TOWN = "{\"maxPrice\": 1000000, \"minBedrooms\": 2, \"types\": [\"condo\", \"townhouse\"]}";

    private final ListingRepository repo = TestData.bundledRepository();
    private final ScriptedLLM llm = new ScriptedLLM();
    private final MemoryManager memory = new MemoryManager();
    private final AdvisorAgent agent = agent();

    private AdvisorAgent agent() {
        ToolManager tools = new ToolManager();
        MortgageCalculator calc = new MortgageCalculator();
        tools.register(new SearchTool(repo));
        tools.register(new MortgageTool(repo, calc));
        tools.register(new ComparisonTool(repo, calc, 2026));
        return new AdvisorAgent(llm, new RequirementExtractor(llm), tools, memory);
    }

    private static List<String> ids(List<Listing> l) {
        return l.stream().map(Listing::getId).toList();
    }

    // ---- search ----

    @Test
    void searchExtractsRunsToolAndExplainsGrounded() {
        llm.decisions.add(SEARCH);
        llm.extractions.add(CONDO_TOWN);
        llm.explanations.add("L002 and L010 both fit your budget; L010 gives more space.");

        AgentResponse r = agent.handleQuery("2 bed condo or townhouse under a million");

        assertEquals(List.of("search"), r.getToolsUsed());
        assertEquals(List.of("L002", "L005", "L010", "L014", "L033", "L038", "L040", "L042"), ids(r.getListings()));
        assertTrue(r.getMessage().startsWith("Found 8 listings matching"));
        assertTrue(r.getMessage().contains("L010 gives more space"));
        assertTrue(r.getNotice().isEmpty());
        assertEquals(8, memory.getLastResults().size());
        assertEquals(1, memory.getHistory().size());
    }

    @Test
    void explanationPromptRestrictsModelToSuppliedListings() {
        llm.decisions.add(SEARCH);
        llm.extractions.add(CONDO_TOWN);
        llm.explanations.add("L002 fits.");
        agent.handleQuery("2 bed condo under a million");
        String prompt = llm.last(AdvisorAgent.EXPLAIN_MARKER);
        assertTrue(prompt.contains("Refer ONLY to the listings below"));
        assertTrue(prompt.contains("- L002:"));
        assertTrue(prompt.contains("- L033:"), "top 5 described");
        assertFalse(prompt.contains("- L038:"), "beyond top 5 not described");
        assertTrue(prompt.contains("8 matched, top 5 shown"));
    }

    @Test
    void inventedListingIsRejectedAndRegeneratedOnce() {
        llm.decisions.add(SEARCH);
        llm.extractions.add(CONDO_TOWN);
        llm.explanations.add("L002 is good, but L999 is even better.");
        llm.explanations.add("L002 is the best fit.");

        AgentResponse r = agent.handleQuery("2 bed condo under a million");

        assertEquals(2, llm.count(AdvisorAgent.EXPLAIN_MARKER));
        String retry = llm.last(AdvisorAgent.REGENERATE_MARKER);
        assertTrue(retry.contains("(L999)"));
        assertTrue(r.getMessage().contains("L002 is the best fit."));
        assertFalse(r.getMessage().contains("L999"));
    }

    @Test
    void twoUngroundedExplanationsShowResultsWithoutNarrative() {
        llm.decisions.add(SEARCH);
        llm.extractions.add(CONDO_TOWN);
        llm.explanations.add("Try L777.");
        llm.explanations.add("Or L888.");

        AgentResponse r = agent.handleQuery("2 bed condo under a million");

        assertEquals(8, r.getListings().size(), "results still render");
        assertFalse(r.getMessage().contains("L777") || r.getMessage().contains("L888"));
        assertEquals("Explanation unavailable; showing results only.", r.getNotice().orElseThrow());
    }

    @Test
    void criteriaAccumulateAcrossTurns() {
        llm.decisions.add(SEARCH);
        llm.extractions.add("{\"maxPrice\": 1000000}");
        llm.explanations.add("Plenty of options.");
        agent.handleQuery("budget is a million");

        llm.decisions.add(SEARCH);
        llm.extractions.add("{\"minBedrooms\": 3, \"types\": [\"house\"]}");
        llm.explanations.add("Fewer now.");
        AgentResponse r = agent.handleQuery("actually I need a 3 bed house");

        assertEquals(1_000_000.0, r.getCriteria().getMaxPrice(), "budget from turn 1 still applies");
        assertTrue(r.getListings().stream().allMatch(l -> l.getPrice() <= 1_000_000 && l.getBedrooms() >= 3));
        assertTrue(llm.last(AdvisorAgent.DECISION_MARKER).contains("User: budget is a million"));
    }

    @Test
    void nothingExtractableAsksAClarifyingQuestion() {
        llm.decisions.add(SEARCH);
        llm.extractions.add("{}");
        AgentResponse r = agent.handleQuery("I want a nice place");
        assertTrue(r.getToolsUsed().isEmpty(), "no search with empty criteria");
        assertTrue(r.getMessage().contains("What matters most?"));
        assertEquals(0, llm.count(AdvisorAgent.EXPLAIN_MARKER));
    }

    @Test
    void unparseableExtractionAsksToRephrase() {
        llm.decisions.add(SEARCH);
        llm.extractions.add("no json");
        llm.extractions.add("still no json");
        AgentResponse r = agent.handleQuery("???");
        assertTrue(r.getMessage().contains("rephrase"));
        assertTrue(r.getListings().isEmpty());
    }

    @Test
    void zeroMatchesStateTheMostRestrictiveRequirementFromCode() {
        llm.decisions.add(SEARCH);
        llm.extractions.add("{\"maxPrice\": 1500000, \"minBedrooms\": 3, \"types\": [\"house\"],"
                + " \"neighborhoods\": [\"Vaughan\", \"Woodbridge\"], \"features\": [\"garage\"]}");
        llm.explanations.add("If the area is flexible, dropping it opens up a few houses with garages.");
        AgentResponse r = agent.handleQuery("3 bed house under 1.5M in Vaughan or Woodbridge with a garage");

        assertTrue(r.getListings().isEmpty());
        assertTrue(r.getMessage().contains("The most restrictive requirement is the area Vaughan or Woodbridge: "
                + "without it, 3 listings match."), r.getMessage());
        String prompt = llm.last(AdvisorAgent.EXPLAIN_MARKER);
        assertTrue(prompt.contains("- Without the feature \"garage\": 3 matches"), "model gets the computed facts");
    }

    @Test
    void zeroMatchesExplainsWithoutListingIds() {
        llm.decisions.add(SEARCH);
        llm.extractions.add("{\"maxPrice\": 100000}");
        llm.explanations.add("Nothing is listed under $100k; raising the budget would help.");
        AgentResponse r = agent.handleQuery("anything under 100k");
        assertTrue(r.getListings().isEmpty());
        assertTrue(r.getMessage().startsWith("No listings match"));
        assertTrue(r.getMessage().contains("raising the budget"));
        assertTrue(llm.last(AdvisorAgent.EXPLAIN_MARKER).contains("No listings matched"));
    }

    @Test
    void preferencesRankButDoNotFilter() {
        // The Oct 31 live test: "maybe in Vaughan or Woodbridge" returned 0 when treated as a filter.
        llm.decisions.add(SEARCH);
        llm.extractions.add("{\"maxPrice\": 1500000, \"minBedrooms\": 3, \"types\": [\"house\"],"
                + " \"preferredNeighborhoods\": [\"Vaughan\", \"Woodbridge\"]}");
        llm.explanations.add("Here are your options.");
        AgentResponse r = agent.handleQuery("3 bedroom house under 1.5M, maybe in Vaughan or Woodbridge");

        assertFalse(r.getListings().isEmpty(), "soft preference must not empty the results");
        assertTrue(r.getCriteria().getNeighborhoods().isEmpty());
        String first = r.getListings().get(0).getNeighborhood();
        assertTrue(first.equals("Vaughan") || first.equals("Woodbridge"), "preferred area ranks first: " + first);
        assertTrue(r.getMessage().contains("Ranked with your preferences"));
        assertTrue(llm.last(AdvisorAgent.EXPLAIN_MARKER).contains("Soft preferences"));
    }

    // ---- tool selection ----

    @Test
    void unknownOrUnparseableToolChoiceFallsBackToSearch() {
        for (String bad : List.of("{\"tool\": \"book_viewing\"}", "I'd search!", "")) {
            ScriptedLLM fresh = new ScriptedLLM();
            fresh.decisions.add(bad);
            fresh.extractions.add("{\"minBedrooms\": 5}");
            fresh.explanations.add("Big homes.");
            ToolManager tools = new ToolManager();
            tools.register(new SearchTool(repo));
            AdvisorAgent a = new AdvisorAgent(fresh, new RequirementExtractor(fresh), tools, new MemoryManager());
            assertEquals(List.of("search"), a.handleQuery("5 bedrooms").getToolsUsed(), bad);
        }
    }

    @Test
    void decisionPromptListsToolsAndRecentResultsWithPositions() {
        llm.decisions.add(SEARCH);
        llm.extractions.add(CONDO_TOWN);
        llm.explanations.add("L002 fits.");
        agent.handleQuery("2 bed condo under a million");

        llm.decisions.add("{\"tool\": \"mortgage\", \"listingIds\": [\"L005\"]}");
        agent.handleQuery("what would the second one cost per month?");

        String decision = llm.last(AdvisorAgent.DECISION_MARKER);
        assertTrue(decision.contains("- search:"));
        assertTrue(decision.contains("- mortgage:"));
        assertTrue(decision.contains("- compare:"));
        assertTrue(decision.contains("2. L005"), "lets the model resolve 'the second one'");
    }

    // ---- mortgage ----

    @Test
    void mortgageFiguresComeFromCalculatorNotModel() {
        llm.decisions.add("{\"tool\": \"mortgage\", \"listingIds\": [\"L001\"], \"annualRate\": 4.8}");
        AgentResponse r = agent.handleQuery("monthly payment for L001 at 4.8%?");

        Listing l = repo.findById("L001").orElseThrow();
        double expected = new MortgageCalculator().monthlyPayment(l.getPrice(),
                new MortgageTerms(l.getPrice() * 0.2, 4.8, 25));
        assertEquals(List.of("mortgage"), r.getToolsUsed());
        assertEquals(expected, r.getMortgage().orElseThrow().getMonthlyPayment(), 1e-9);
        assertTrue(r.getMessage().contains(String.format(Locale.ROOT, "$%,.2f a month", expected)), r.getMessage());
        assertTrue(r.getNotice().orElseThrow().contains("affordability"));
        assertEquals(0, llm.count(AdvisorAgent.EXPLAIN_MARKER), "no generated text for figures");
    }

    @Test
    void mortgageWithProfileIncludesVerdict() {
        agent.setFinancialProfile(new FinancialProfile(400_000, 0, 500_000));
        llm.decisions.add("{\"tool\": \"mortgage\", \"listingIds\": [\"L002\"]}");
        AgentResponse r = agent.handleQuery("can I afford L002?");
        assertTrue(r.getAffordability().isPresent());
        assertTrue(r.getMessage().contains("Affordability: Affordable"));
        assertTrue(r.getNotice().isEmpty());
    }

    @Test
    void mortgageWithoutAResolvableListingAsksWhich() {
        llm.decisions.add("{\"tool\": \"mortgage\"}");
        AgentResponse r = agent.handleQuery("how much per month?");
        assertTrue(r.getMessage().contains("Which property"));
    }

    @Test
    void mortgageForUnknownIdReportsIt() {
        llm.decisions.add("{\"tool\": \"mortgage\", \"listingIds\": [\"L999\"]}");
        assertEquals("No listing with ID L999", agent.handleQuery("payment on L999").getMessage());
    }

    // ---- compare ----

    @Test
    void compareBuildsMatrixAndGroundedSummary() {
        llm.decisions.add("{\"tool\": \"compare\", \"listingIds\": [\"L002\", \"L005\"]}");
        llm.explanations.add("L002 is cheaper while L005 is larger.");
        AgentResponse r = agent.handleQuery("compare L002 and L005");
        assertEquals(List.of("compare"), r.getToolsUsed());
        assertEquals(List.of("L002", "L005"), ids(r.getComparison().orElseThrow().getListings()));
        assertTrue(r.getMessage().contains("L005 is larger"));
        assertTrue(llm.last(AdvisorAgent.EXPLAIN_MARKER).contains("Comparison table:"));
    }

    @Test
    void compareWithoutIdsUsesTopTwoResults() {
        llm.decisions.add(SEARCH);
        llm.extractions.add(CONDO_TOWN);
        llm.explanations.add("L002 fits.");
        agent.handleQuery("2 bed condo under a million");
        llm.decisions.add("{\"tool\": \"compare\"}");
        llm.explanations.add("L002 versus L005.");
        AgentResponse r = agent.handleQuery("how do the top two compare?");
        assertEquals(List.of("L002", "L005"), ids(r.getListings()));
    }

    @Test
    void directCompareWithModelDownStillShowsMatrix() {
        llm.available = false;
        AgentResponse r = agent.compareProperties(List.of("L001", "L002", "L003"));
        assertEquals(3, r.getComparison().orElseThrow().getListings().size());
        assertTrue(r.getNotice().orElseThrow().contains("comparison table is still accurate"));
        assertFalse(r.isAiAvailable());
    }

    @Test
    void compareFewerThanTwoIsReported() {
        AgentResponse r = agent.compareProperties(List.of("L001"));
        assertTrue(r.getMessage().contains("at least two"));
        assertTrue(r.getComparison().isEmpty());
    }

    // ---- direct reply / availability ----

    @Test
    void noToolReplyIsReturnedButUngroundedIdsAreBlocked() {
        llm.decisions.add("{\"tool\": \"none\", \"reply\": \"A pre-approval tells you your budget.\"}");
        assertEquals("A pre-approval tells you your budget.", agent.handleQuery("what is a pre-approval?").getMessage());

        llm.decisions.add("{\"tool\": \"none\", \"reply\": \"You should look at L042.\"}");
        AgentResponse r = agent.handleQuery("any tips?");
        assertTrue(r.getMessage().contains("only discuss properties from your search results"));
        assertTrue(r.getToolsUsed().isEmpty());
    }

    @Test
    void unavailableModelFallsBackWithoutCalling() {
        llm.available = false;
        AgentResponse r = agent.handleQuery("3 bed house");
        assertFalse(r.isAiAvailable());
        assertTrue(r.getMessage().contains("criteria panel"));
        assertTrue(llm.prompts.isEmpty());
    }

    @Test
    void modelFailureMidRequestIsReportedNotThrown() {
        llm.failDecision = true;
        AgentResponse r = agent.handleQuery("3 bed house");
        assertFalse(r.isAiAvailable());
        assertTrue(r.getMessage().contains("overloaded"));
        assertEquals(1, memory.getHistory().size());
    }

    @Test
    void blankQueryPromptsForInput() {
        assertTrue(agent.handleQuery("  ").getMessage().startsWith("Tell me what you're looking for"));
        assertTrue(llm.prompts.isEmpty());
    }

    // ---- grounding check ----

    @Test
    void groundingAcceptsOnlySuppliedIds() {
        List<Listing> supplied = List.of(repo.findById("L001").orElseThrow(), repo.findById("L002").orElseThrow());
        assertTrue(agent.validateGrounding("L001 is cheaper than L002.", supplied));
        assertTrue(agent.validateGrounding("Both are condos.", supplied));
        assertFalse(agent.validateGrounding("Consider L003 instead.", supplied));
        assertFalse(agent.validateGrounding("", supplied));
        assertFalse(agent.validateGrounding("L001", List.of()));
    }
}
