package ca.yorku.realestate.agent;

import ca.yorku.realestate.agent.llm.LLMClient;
import ca.yorku.realestate.agent.llm.LLMException;
import ca.yorku.realestate.domain.PropertyType;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.domain.SearchCriteriaBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns a free-text request into structured {@link SearchCriteria} using the language model
 * (F02). This is the AI-driven half of natural-language search; filtering and ranking of the
 * extracted criteria stay deterministic.
 *
 * <p>The model is asked for a JSON object matching {@link #SCHEMA}. Its reply is parsed and
 * validated here rather than trusted: code fences and surrounding prose are stripped, numbers
 * must be numbers, and only known property types are accepted. If the first reply cannot be
 * parsed, one retry is made with a stricter prompt; if that also fails an
 * {@link ExtractionException} asks the user to rephrase.
 *
 * <p>When known neighborhoods and features are supplied, they are listed in the prompt so the
 * model maps phrases like "near downtown" onto values that exist in the data set.
 *
 * <p>Hedged wishes ("maybe in Vaughan", "ideally a pool") are extracted separately as
 * preferences by {@link #extractRequest}, so they rank results instead of filtering them out.
 */
public class RequirementExtractor {

    /** JSON Schema the model's reply must follow. Every field is optional. */
    public static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "minPrice":     {"type": ["number", "null"], "description": "Lowest acceptable price in dollars"},
                "maxPrice":     {"type": ["number", "null"], "description": "Highest acceptable price in dollars"},
                "minBedrooms":  {"type": ["integer", "null"]},
                "minBathrooms": {"type": ["integer", "null"]},
                "types":        {"type": "array", "items": {"enum": ["house", "condo", "townhouse"]}},
                "neighborhoods":{"type": "array", "items": {"type": "string"}},
                "features":     {"type": "array", "items": {"type": "string"}},
                "preferredTypes":         {"type": "array", "items": {"enum": ["house", "condo", "townhouse"]}},
                "preferredNeighborhoods": {"type": "array", "items": {"type": "string"}},
                "preferredFeatures":      {"type": "array", "items": {"type": "string"}}
              },
              "additionalProperties": false
            }""";

    /** Marker that distinguishes the retry prompt (also lets tests target it). */
    static final String RETRY_MARKER = "Your previous reply could not be parsed.";

    private final LLMClient llm;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> knownNeighborhoods = new TreeSet<>();
    private final Set<String> knownFeatures = new TreeSet<>();

    public RequirementExtractor(LLMClient llm) {
        if (llm == null) {
            throw new IllegalArgumentException("LLM client is required");
        }
        this.llm = llm;
    }

    /** Lists the data set's actual neighborhoods and features in the prompt. */
    public RequirementExtractor withVocabulary(Collection<String> neighborhoods, Collection<String> features) {
        knownNeighborhoods.clear();
        knownFeatures.clear();
        if (neighborhoods != null) {
            knownNeighborhoods.addAll(neighborhoods);
        }
        if (features != null) {
            knownFeatures.addAll(features);
        }
        return this;
    }

    /**
     * Extracts criteria from the user's text. Fields the user did not mention are left unset,
     * so the result can be merged into the session criteria. A request with nothing
     * extractable returns empty criteria; the caller should ask a clarifying question rather
     * than search with them.
     *
     * @throws LLMException if the model is unavailable or the call fails
     * @throws ExtractionException if two replies in a row could not be parsed
     */
    public SearchCriteria extract(String userText) {
        return extractRequest(userText).required();
    }

    /**
     * Extracts hard requirements and soft preferences.
     *
     * @throws LLMException if the model is unavailable or the call fails
     * @throws ExtractionException if two replies in a row could not be parsed
     */
    public ExtractedRequest extractRequest(String userText) {
        if (userText == null || userText.isBlank()) {
            return new ExtractedRequest(null, null);
        }
        if (!llm.isAvailable()) {
            throw new LLMException("The language model is not available.");
        }
        String reply = llm.completeStructured(buildPrompt(userText), SCHEMA);
        try {
            return parseRequest(reply);
        } catch (IllegalArgumentException first) {
            String retry = llm.completeStructured(buildStrictPrompt(userText, first.getMessage()), SCHEMA);
            try {
                return parseRequest(retry);
            } catch (IllegalArgumentException second) {
                throw new ExtractionException("Sorry, I couldn't work out what you're looking for. "
                        + "Could you rephrase, e.g. \"3 bedroom house under $850k in Maple\"?", second);
            }
        }
    }

    String buildPrompt(String userText) {
        StringBuilder p = new StringBuilder();
        p.append("Extract home search criteria from the buyer's request below.\n")
         .append("Rules:\n")
         .append("- Include only criteria the buyer actually stated; omit everything else.\n")
         .append("- Prices are plain numbers in dollars (\"850k\" -> 850000, \"1.2M\" -> 1200000).\n")
         .append("- \"under X\" or \"at most X\" sets maxPrice; \"at least N bedrooms\" or \"N bedroom\" sets minBedrooms.\n")
         .append("- types must be from: house, condo, townhouse.\n")
         .append("- Hedged wishes (\"maybe\", \"ideally\", \"preferably\", \"would be nice\", \"bonus if\") go in\n")
         .append("  preferredTypes / preferredNeighborhoods / preferredFeatures, NOT in the hard fields.\n")
         .append("  Firm requirements (\"must\", \"need\", or stated plainly) go in the hard fields.\n");
        if (!knownNeighborhoods.isEmpty()) {
            p.append("- neighborhoods must be from: ").append(String.join(", ", knownNeighborhoods)).append('\n');
        }
        if (!knownFeatures.isEmpty()) {
            p.append("- features must be from: ").append(String.join(", ", knownFeatures)).append('\n');
        }
        p.append("\nBuyer's request:\n\"\"\"\n").append(userText.trim()).append("\n\"\"\"");
        return p.toString();
    }

    String buildStrictPrompt(String userText, String problem) {
        return RETRY_MARKER + " Problem: " + problem + "\n"
                + "Reply with ONLY a single JSON object, starting with { and ending with }. "
                + "Use numbers, not strings, for prices and counts.\n\n" + buildPrompt(userText);
    }

    /** Parses the model's reply into hard criteria only. */
    SearchCriteria parseResponse(String reply) {
        return parseRequest(reply).required();
    }

    /**
     * Parses the model's reply into requirements and preferences.
     *
     * @throws IllegalArgumentException describing what was wrong with the reply
     */
    ExtractedRequest parseRequest(String reply) {
        JsonNode root = readObject(reply);
        SearchCriteriaBuilder p = new SearchCriteriaBuilder();
        for (String t : stringArray(root, "preferredTypes")) {
            p.withType(PropertyType.fromString(t));
        }
        for (String n : stringArray(root, "preferredNeighborhoods")) {
            p.withNeighborhood(n);
        }
        for (String f : stringArray(root, "preferredFeatures")) {
            p.withFeature(f);
        }
        return new ExtractedRequest(parseRequired(root), p.build());
    }

    private SearchCriteria parseRequired(JsonNode root) {
        SearchCriteriaBuilder b = new SearchCriteriaBuilder();
        b.withMinPrice(optionalNumber(root, "minPrice"));
        b.withMaxPrice(optionalNumber(root, "maxPrice"));
        b.withMinBedrooms(optionalInt(root, "minBedrooms"));
        b.withMinBathrooms(optionalInt(root, "minBathrooms"));
        for (String t : stringArray(root, "types")) {
            b.withType(PropertyType.fromString(t));
        }
        for (String n : stringArray(root, "neighborhoods")) {
            b.withNeighborhood(n);
        }
        for (String f : stringArray(root, "features")) {
            b.withFeature(f);
        }
        return b.build();
    }

    private JsonNode readObject(String reply) {
        if (reply == null || reply.isBlank()) {
            throw new IllegalArgumentException("reply was empty");
        }
        String text = reply.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("reply contained no JSON object");
        }
        try {
            JsonNode node = mapper.readTree(text.substring(start, end + 1));
            if (!node.isObject()) {
                throw new IllegalArgumentException("reply was not a JSON object");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("reply was not valid JSON: " + e.getOriginalMessage());
        }
    }

    private static Double optionalNumber(JsonNode root, String field) {
        JsonNode v = root.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        double d = v.asDouble();
        if (!Double.isFinite(d) || d < 0) {
            throw new IllegalArgumentException(field + " must be a non-negative number");
        }
        return d;
    }

    private static Integer optionalInt(JsonNode root, String field) {
        Double d = optionalNumber(root, field);
        if (d == null) {
            return null;
        }
        if (d != Math.rint(d)) {
            throw new IllegalArgumentException(field + " must be a whole number");
        }
        return d.intValue();
    }

    private static List<String> stringArray(JsonNode root, String field) {
        JsonNode v = root.get(field);
        if (v == null || v.isNull()) {
            return List.of();
        }
        if (!v.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        List<String> out = new java.util.ArrayList<>();
        for (JsonNode item : v) {
            if (!item.isTextual()) {
                throw new IllegalArgumentException(field + " must contain only strings");
            }
            if (!item.asText().isBlank()) {
                out.add(item.asText().trim());
            }
        }
        return out;
    }
}
