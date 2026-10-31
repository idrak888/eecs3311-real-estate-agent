package ca.yorku.realestate.app;

import ca.yorku.realestate.agent.ExtractionException;
import ca.yorku.realestate.agent.RequirementExtractor;
import ca.yorku.realestate.agent.llm.AnthropicLLMClient;
import ca.yorku.realestate.agent.llm.LLMException;
import ca.yorku.realestate.config.EnvConfig;
import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.persistence.ListingImporter;
import ca.yorku.realestate.persistence.ListingRepository;
import ca.yorku.realestate.persistence.parser.ListingParserFactory;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * One-off check that the LLM integration works end to end: reads the key from {@code .env},
 * sends one natural-language request through {@link RequirementExtractor}, and runs the
 * extracted criteria against the bundled listings. Prints token usage for the AI log.
 *
 * <p>Run from the project root (so {@code .env} is found). Optional argument: the request text.
 * Never prints the API key.
 */
public final class LlmConnectionCheck {

    private LlmConnectionCheck() {
    }

    public static void main(String[] args) throws Exception {
        String request = args.length > 0 ? String.join(" ", args)
                : "3 bedroom house under 1.5M with a garage, maybe in Vaughan or Woodbridge";

        EnvConfig config = EnvConfig.load();
        AnthropicLLMClient llm = AnthropicLLMClient.fromConfig(config);
        System.out.println("Working directory: " + new File(".").getAbsoluteFile().getParent());
        System.out.println("API key configured: " + llm.isAvailable());
        System.out.println("Model: " + llm.getModel());
        if (!llm.isAvailable()) {
            System.out.println("No key found. Run from the project root, and check .env has LLM_API_KEY=...");
            System.exit(1);
        }

        ListingRepository repo = new ListingRepository();
        URL data = LlmConnectionCheck.class.getResource("/data/listings.json");
        new ListingImporter(new ListingParserFactory(), repo).importFile(new File(data.toURI()));
        Set<String> neighborhoods = new TreeSet<>();
        Set<String> features = new TreeSet<>();
        for (Listing l : repo.findAll()) {
            neighborhoods.add(l.getNeighborhood());
            features.addAll(l.getFeatures());
        }

        RequirementExtractor extractor = new RequirementExtractor(llm).withVocabulary(neighborhoods, features);
        System.out.println("\nRequest:   " + request);
        try {
            long start = System.currentTimeMillis();
            SearchCriteria criteria = extractor.extract(request);
            long ms = System.currentTimeMillis() - start;
            System.out.println("Extracted: " + criteria.describe());
            List<Listing> matches = repo.findMatching(criteria.toFilter());
            System.out.println("Matches:   " + matches.size() + " of " + repo.count() + " listings");
            matches.stream().limit(5).forEach(l -> System.out.println("  " + l));
            System.out.println("\nOK in " + ms + " ms. Tokens used: " + llm.getInputTokens()
                    + " input, " + llm.getOutputTokens() + " output.");
        } catch (LLMException e) {
            System.out.println("LLM call failed: " + e.getMessage());
            System.exit(2);
        } catch (ExtractionException e) {
            System.out.println("Model replied but the reply could not be parsed: " + e.getCause().getMessage());
            System.exit(3);
        }
    }
}
