package ca.yorku.realestate.agent;

import ca.yorku.realestate.agent.llm.LLMClient;
import ca.yorku.realestate.agent.llm.LLMException;
import ca.yorku.realestate.agent.tool.ComparisonResult;
import ca.yorku.realestate.agent.tool.ComparisonTool;
import ca.yorku.realestate.agent.tool.MortgageTool;
import ca.yorku.realestate.agent.tool.SearchTool;
import ca.yorku.realestate.agent.tool.ToolManager;
import ca.yorku.realestate.agent.tool.ToolRequest;
import ca.yorku.realestate.agent.tool.ToolResult;
import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.domain.finance.AffordabilityResult;
import ca.yorku.realestate.domain.finance.FinancialProfile;
import ca.yorku.realestate.domain.finance.MortgageResult;
import ca.yorku.realestate.domain.finance.MortgageTerms;
import ca.yorku.realestate.domain.ranking.RankingStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The conversational advisor (F02, F08, F10).
 *
 * <p>Each request goes through three steps:
 * <ol>
 *   <li><b>Tool selection</b>: the model reads the request, the tool descriptions and the
 *       conversation context, and picks one of search, mortgage, compare or none, with any
 *       parameters (e.g. which listing "the second one" refers to). The choice is validated in
 *       code: an unknown tool or unparseable reply falls back to search.</li>
 *   <li><b>Deterministic execution</b>: the chosen tool computes the result. The model never
 *       produces a listing, price or payment itself.</li>
 *   <li><b>Grounded explanation</b>: the model explains the result, restricted to the supplied
 *       listings. Every listing ID in the explanation must be one it was given; otherwise the
 *       explanation is regenerated once and then dropped, so results show without narrative.</li>
 * </ol>
 * The agent advises but never acts: it does not transact or contact anyone.
 */
public class AdvisorAgent {

    /** Tool choice meaning "answer directly, no tool needed". */
    public static final String NO_TOOL = "none";

    /** Listing IDs in explanations look like the data set's IDs, e.g. L014. */
    static final Pattern LISTING_ID = Pattern.compile("\\b[A-Z]{1,3}\\d{2,4}\\b");

    /** Listings described in an explanation prompt; the rest are summarized as a count. */
    static final int MAX_EXPLAINED = 5;

    static final String DECISION_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "tool": {"enum": ["search", "mortgage", "compare", "none"]},
                "listingIds": {"type": "array", "items": {"type": "string"}},
                "downPayment": {"type": ["number", "null"]},
                "annualRate": {"type": ["number", "null"], "description": "percent, e.g. 4.8"},
                "years": {"type": ["integer", "null"]},
                "reply": {"type": ["string", "null"], "description": "only when tool is none"}
              },
              "required": ["tool"]
            }""";

    static final String DECISION_MARKER = "Decide which tool the advisor should use";
    static final String EXPLAIN_MARKER = "Explain these results to the buyer";
    static final String REGENERATE_MARKER = "Your previous explanation mentioned listings that were not supplied";

    private final LLMClient llm;
    private final RequirementExtractor extractor;
    private final ToolManager tools;
    private final MemoryManager memory;
    private final ObjectMapper mapper = new ObjectMapper();

    private FinancialProfile profile;
    private RankingStrategy rankingStrategy;

    public AdvisorAgent(LLMClient llm, RequirementExtractor extractor, ToolManager tools, MemoryManager memory) {
        this.llm = llm;
        this.extractor = extractor;
        this.tools = tools;
        this.memory = memory;
    }

    /** Profile used for affordability in mortgage answers; null clears it. */
    public void setFinancialProfile(FinancialProfile profile) {
        this.profile = profile;
    }

    /** Ranking applied to agent searches; null means best match. */
    public void setRankingStrategy(RankingStrategy strategy) {
        this.rankingStrategy = strategy;
    }

    public MemoryManager getMemory() {
        return memory;
    }

    // ---- entry points ----

    /** Handles one chat message or CLI {@code ask}. Never throws for model failures. */
    public AgentResponse handleQuery(String text) {
        if (text == null || text.isBlank()) {
            return AgentResponse.builder().message("Tell me what you're looking for, e.g. "
                    + "\"3 bedroom house under $900k with a garage\".").build();
        }
        if (!llm.isAvailable()) {
            return AgentResponse.unavailable("The AI assistant isn't available right now (no API key "
                    + "configured). You can still search with the criteria panel or the CLI search command.");
        }
        AgentResponse response;
        try {
            Decision d = selectTool(text);
            response = switch (d.tool) {
                case MortgageTool.NAME -> handleMortgage(d);
                case ComparisonTool.NAME -> handleCompare(d);
                case NO_TOOL -> handleDirect(d);
                default -> handleSearch(text);
            };
        } catch (LLMException e) {
            response = AgentResponse.unavailable("The AI assistant couldn't be reached (" + e.getMessage()
                    + "). You can still search with the criteria panel or the CLI search command.");
        }
        memory.addTurn(new ConversationTurn(text, response.getMessage()));
        return response;
    }

    /** Direct comparison from the GUI Compare button or CLI {@code compare}; no tool selection. */
    public AgentResponse compareProperties(List<String> listingIds) {
        AgentResponse r = runComparison(listingIds);
        memory.addTurn(new ConversationTurn("compare " + String.join(" ", listingIds), r.getMessage()));
        return r;
    }

    // ---- step 1: tool selection ----

    /** The model's choice of tool and parameters, validated. */
    static final class Decision {
        final String tool;
        final List<String> listingIds;
        final Double downPayment;
        final Double annualRate;
        final Integer years;
        final String reply;

        Decision(String tool, List<String> listingIds, Double downPayment, Double annualRate,
                 Integer years, String reply) {
            this.tool = tool;
            this.listingIds = listingIds;
            this.downPayment = downPayment;
            this.annualRate = annualRate;
            this.years = years;
            this.reply = reply;
        }
    }

    Decision selectTool(String text) {
        String prompt = DECISION_MARKER + " for the buyer's latest message.\n\n"
                + "Available tools:\n" + tools.describeTools()
                + "- none: no tool needed (greetings, general questions about buying a home); "
                + "put a short helpful answer in \"reply\". Never state prices or payments here.\n\n"
                + "Rules:\n"
                + "- Any new or changed requirement (budget, bedrooms, type, area, features) means search.\n"
                + "- Questions about cost, monthly payment, interest or affording ONE property mean mortgage.\n"
                + "- Asking how properties stack up, or which of several is better, means compare.\n"
                + "- Resolve references like \"the second one\" or \"the cheapest\" to listing IDs using the "
                + "most recent results below. Only use IDs that appear there.\n\n"
                + memory.getContext(3)
                + "\nBuyer's latest message:\n\"\"\"\n" + text.trim() + "\n\"\"\"";
        String reply = llm.completeStructured(prompt, DECISION_SCHEMA);
        return parseDecision(reply);
    }

    Decision parseDecision(String reply) {
        try {
            int start = reply.indexOf('{');
            int end = reply.lastIndexOf('}');
            JsonNode n = mapper.readTree(reply.substring(start, end + 1));
            String tool = n.path("tool").asText("").trim().toLowerCase(Locale.ROOT);
            if (!tool.equals(NO_TOOL) && tools.get(tool).isEmpty()) {
                tool = SearchTool.NAME;
            }
            List<String> ids = new ArrayList<>();
            for (JsonNode id : n.path("listingIds")) {
                if (id.isTextual() && !id.asText().isBlank()) {
                    ids.add(id.asText().trim().toUpperCase(Locale.ROOT));
                }
            }
            return new Decision(tool, ids, number(n, "downPayment"), number(n, "annualRate"),
                    n.path("years").isNumber() ? n.path("years").asInt() : null,
                    n.path("reply").isTextual() ? n.path("reply").asText() : null);
        } catch (Exception e) {
            // Unparseable choice: searching is the safe default, since it only reads data.
            return new Decision(SearchTool.NAME, List.of(), null, null, null, null);
        }
    }

    private static Double number(JsonNode n, String field) {
        return n.path(field).isNumber() ? n.path(field).asDouble() : null;
    }

    // ---- step 2: execution ----

    private AgentResponse handleSearch(String text) {
        SearchCriteria extracted;
        try {
            extracted = extractor.extract(text);
        } catch (ExtractionException e) {
            return AgentResponse.builder().message(e.getMessage()).criteria(memory.getCriteria()).build();
        }
        if (extracted.isEmpty() && memory.getCriteria().isEmpty()) {
            return AgentResponse.builder().message("Happy to help. What matters most? For example a "
                    + "budget, number of bedrooms, property type, or neighborhood.").build();
        }
        SearchCriteria criteria = memory.mergeCriteria(extracted);
        ToolRequest request = ToolRequest.empty()
                .with(SearchTool.CRITERIA, criteria)
                .with(SearchTool.RANKING_CRITERIA, memory.getRankingCriteria())
                .with(SearchTool.STRATEGY, rankingStrategy);
        ToolResult result = tools.execute(SearchTool.NAME, request);
        if (!result.isSuccess()) {
            return AgentResponse.builder().tool(SearchTool.NAME).criteria(criteria)
                    .message("I couldn't run that search: " + result.getErrorMessage()).build();
        }
        List<Listing> listings = result.getListings();
        memory.setLastResults(listings);

        AgentResponse.Builder b = AgentResponse.builder().tool(SearchTool.NAME).criteria(criteria).listings(listings);
        String summary = listings.isEmpty() ? "No listings match " + criteria.describe() + "."
                : "Found " + listings.size() + (listings.size() == 1 ? " listing" : " listings")
                + " matching " + criteria.describe() + ".";
        String explanation;
        try {
            explanation = explainOrNull(listings, criteria, null);
        } catch (LLMException e) {
            explanation = null; // results are still valid; only the narrative is lost
        }
        if (explanation == null) {
            return b.message(summary).notice("Explanation unavailable; showing results only.").build();
        }
        return b.message(summary + "\n\n" + explanation).build();
    }

    private AgentResponse handleMortgage(Decision d) {
        String id = !d.listingIds.isEmpty() ? d.listingIds.get(0)
                : memory.getLastResults().size() == 1 ? memory.getLastResults().get(0).getId() : null;
        if (id == null) {
            return AgentResponse.builder().message("Which property should I run the numbers on? "
                    + "Give me its ID, or search first and say e.g. \"the first one\".").build();
        }
        ToolRequest request = ToolRequest.empty().with(MortgageTool.LISTING_ID, id)
                .with(MortgageTool.DOWN_PAYMENT, d.downPayment)
                .with(MortgageTool.RATE, d.annualRate)
                .with(MortgageTool.YEARS, d.years)
                .with(MortgageTool.PROFILE, profile);
        ToolResult r = tools.execute(MortgageTool.NAME, request);
        if (!r.isSuccess()) {
            return AgentResponse.builder().tool(MortgageTool.NAME).message(r.getErrorMessage()).build();
        }
        Listing listing = r.getListings().get(0);
        MortgageResult m = r.getData(MortgageTool.MORTGAGE, MortgageResult.class).orElseThrow();
        MortgageTerms terms = r.getData("terms", MortgageTerms.class).orElseThrow();
        AffordabilityResult a = r.getData(MortgageTool.AFFORDABILITY, AffordabilityResult.class).orElse(null);
        // Figures are formatted here from the calculator's output; the model never writes them.
        String message = describeMortgage(listing, m, terms, a);
        return AgentResponse.builder().tool(MortgageTool.NAME).listings(List.of(listing))
                .mortgage(m).affordability(a).message(message)
                .notice(a == null ? "Add your income, debt and down payment to see an affordability verdict." : null)
                .build();
    }

    static String describeMortgage(Listing l, MortgageResult m, MortgageTerms t, AffordabilityResult a) {
        StringBuilder sb = new StringBuilder();
        if (!m.isFinancingRequired()) {
            sb.append(String.format(Locale.ROOT, "%s: your $%,.0f down payment covers the $%,.0f price, "
                    + "so no mortgage is needed.", l.getId(), t.getDownPayment(), l.getPrice()));
        } else {
            sb.append(String.format(Locale.ROOT, "%s at $%,.0f: about $%,.2f a month, borrowing $%,.0f "
                    + "at %.2f%% over %d years. Total interest $%,.0f; total cost $%,.0f.",
                    l.getId(), l.getPrice(), m.getMonthlyPayment(), m.getPrincipal(), t.getAnnualRate(),
                    t.getYears(), m.getTotalInterest(), m.getTotalCost()));
        }
        if (a != null) {
            sb.append("\nAffordability: ").append(a.getVerdict().getLabel()).append(". ").append(a.getExplanation());
            if (a.getMaxPrice() > 0) {
                sb.append(String.format(Locale.ROOT, " Your maximum supportable price is about $%,.0f.", a.getMaxPrice()));
            }
        }
        return sb.toString();
    }

    private AgentResponse handleCompare(Decision d) {
        List<String> ids = d.listingIds;
        if (ids.size() < ComparisonTool.MIN_LISTINGS && memory.getLastResults().size() >= 2) {
            ids = List.of(memory.getLastResults().get(0).getId(), memory.getLastResults().get(1).getId());
        }
        return runComparison(ids);
    }

    private AgentResponse runComparison(List<String> ids) {
        ToolResult r = tools.execute(ComparisonTool.NAME,
                ToolRequest.empty().with(ComparisonTool.LISTING_IDS, ids == null ? List.of() : ids));
        if (!r.isSuccess()) {
            return AgentResponse.builder().tool(ComparisonTool.NAME).message(r.getErrorMessage()).build();
        }
        ComparisonResult c = r.getData(ComparisonTool.COMPARISON, ComparisonResult.class).orElseThrow();
        AgentResponse.Builder b = AgentResponse.builder().tool(ComparisonTool.NAME)
                .listings(c.getListings()).comparison(c);
        boolean capped = r.getData(ComparisonTool.CAPPED, Boolean.class).orElse(false);
        String header = "Comparing " + String.join(", ", c.getListings().stream().map(Listing::getId).toList())
                + (capped ? " (capped at " + ComparisonTool.MAX_LISTINGS + ")" : "") + ".";
        if (!llm.isAvailable()) {
            return b.message(header).aiAvailable(false)
                    .notice("AI summary unavailable; the comparison table is still accurate.").build();
        }
        String summary;
        try {
            summary = explainOrNull(c.getListings(), memory.getCriteria(), c);
        } catch (LLMException e) {
            summary = null;
        }
        if (summary == null) {
            return b.message(header).notice("Summary unavailable; the comparison table is still accurate.").build();
        }
        return b.message(header + "\n\n" + summary).build();
    }

    private AgentResponse handleDirect(Decision d) {
        String reply = d.reply == null || d.reply.isBlank()
                ? "I can search listings, estimate mortgage payments and affordability, and compare properties. "
                + "What are you looking for?"
                : d.reply.trim();
        if (!validateGrounding(reply, memory.getLastResults())) {
            reply = "I can only discuss properties from your search results. Try searching first, "
                    + "or ask about one of the listings shown.";
        }
        return AgentResponse.builder().message(reply).build();
    }

    // ---- step 3: grounded explanation ----

    /**
     * Explains results or a comparison in plain language, grounded in the supplied listings.
     *
     * @return the explanation, or null if the model could not produce a grounded one
     */
    public String explainResults(List<Listing> listings, SearchCriteria criteria) {
        return explainOrNull(listings, criteria, null);
    }

    private String explainOrNull(List<Listing> listings, SearchCriteria criteria, ComparisonResult comparison) {
        List<Listing> shown = listings.subList(0, Math.min(MAX_EXPLAINED, listings.size()));
        String prompt = buildExplanationPrompt(listings, shown, criteria, comparison);
        String text = llm.complete(prompt);
        if (validateGrounding(text, shown)) {
            return text.trim();
        }
        Set<String> invented = unknownIds(text, shown);
        String retry = REGENERATE_MARKER + " (" + String.join(", ", invented) + "). Mention ONLY these IDs: "
                + String.join(", ", shown.stream().map(Listing::getId).toList()) + ".\n\n" + prompt;
        String second = llm.complete(retry);
        return validateGrounding(second, shown) ? second.trim() : null;
    }

    String buildExplanationPrompt(List<Listing> all, List<Listing> shown, SearchCriteria criteria,
                                  ComparisonResult comparison) {
        StringBuilder p = new StringBuilder();
        p.append(EXPLAIN_MARKER).append(" in 3 to 5 sentences.\n")
         .append("Rules:\n")
         .append("- Refer ONLY to the listings below, by their IDs. Never mention any other property.\n")
         .append("- Use only the figures given below; do not calculate or estimate prices or payments.\n")
         .append("- Explain why the options fit the buyer's priorities and what each one gives up.\n")
         .append("- Point out where the buyer's requirements conflict, if they do.\n\n");
        p.append("Buyer's criteria: ").append(criteria == null || criteria.isEmpty() ? "none" : criteria.describe()).append('\n');
        if (!memory.getPreferences().isEmpty()) {
            p.append("Soft preferences: ").append(memory.getPreferences().describe()).append('\n');
        }
        if (comparison != null) {
            p.append("\nComparison table:\n").append(comparison.toTable());
        } else if (shown.isEmpty()) {
            p.append("\nNo listings matched. Explain briefly which requirement is most likely too strict "
                    + "and suggest one change. Do not mention any listing IDs.\n");
        } else {
            p.append("\nListings (").append(all.size()).append(" matched");
            if (all.size() > shown.size()) {
                p.append(", top ").append(shown.size()).append(" shown");
            }
            p.append("):\n");
            for (Listing l : shown) {
                p.append(String.format(Locale.ROOT, "- %s: %s in %s, $%,.0f, %d bed, %d bath, %s, built %d, features: %s%n",
                        l.getId(), l.getType().name().toLowerCase(Locale.ROOT), l.getNeighborhood(), l.getPrice(),
                        l.getBedrooms(), l.getBathrooms(),
                        l.hasSquareFeet() ? String.format(Locale.ROOT, "%,d sq ft", l.getSquareFeet()) : "size unknown",
                        l.getYearBuilt(), l.getFeatures().isEmpty() ? "none" : String.join(", ", l.getFeatures())));
            }
        }
        p.append('\n').append(memory.getContext(2));
        return p.toString();
    }

    /** True when every listing ID mentioned in the text is one of the supplied listings. */
    public boolean validateGrounding(String text, List<Listing> supplied) {
        return text != null && !text.isBlank() && unknownIds(text, supplied).isEmpty();
    }

    static Set<String> unknownIds(String text, List<Listing> supplied) {
        Set<String> allowed = new LinkedHashSet<>();
        for (Listing l : supplied) {
            allowed.add(l.getId());
        }
        Set<String> unknown = new LinkedHashSet<>();
        if (text == null) {
            return unknown;
        }
        Matcher m = LISTING_ID.matcher(text);
        while (m.find()) {
            if (!allowed.contains(m.group())) {
                unknown.add(m.group());
            }
        }
        return unknown;
    }
}
