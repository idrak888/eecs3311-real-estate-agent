package ca.yorku.realestate.agent.tool;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.domain.ZeroMatchAnalyzer;
import ca.yorku.realestate.domain.ranking.BestMatchStrategy;
import ca.yorku.realestate.domain.ranking.RankingStrategy;
import ca.yorku.realestate.persistence.ListingRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Queries the listing repository with structured criteria (F02, F03).
 *
 * <p>Parameters: {@value #CRITERIA} (SearchCriteria, required), {@value #RANKING_CRITERIA}
 * (SearchCriteria including soft preferences, optional; defaults to the criteria) and
 * {@value #STRATEGY} (RankingStrategy, optional; defaults to best match).
 * Data returned: {@value #COUNT}, {@value #TOTAL} and, when nothing matched,
 * {@value #DIAGNOSIS} (a {@link ZeroMatchAnalyzer.Diagnosis} naming the most restrictive requirement).
 */
public class SearchTool implements AgentTool {

    public static final String NAME = "search";
    public static final String CRITERIA = "criteria";
    public static final String RANKING_CRITERIA = "rankingCriteria";
    public static final String STRATEGY = "strategy";
    public static final String COUNT = "count";
    public static final String TOTAL = "total";
    public static final String DIAGNOSIS = "diagnosis";

    private final ListingRepository repository;

    public SearchTool(ListingRepository repository) {
        this.repository = repository;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "find listings matching the buyer's requirements (price, bedrooms, bathrooms, "
                + "type, neighborhood, features); use for any new or refined search";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        SearchCriteria criteria = request.get(CRITERIA, SearchCriteria.class).orElse(null);
        if (criteria == null) {
            return ToolResult.failure("Search needs criteria");
        }
        if (!criteria.isValid()) {
            return ToolResult.failure(String.join("; ", criteria.validationErrors()));
        }
        SearchCriteria rankBy = request.get(RANKING_CRITERIA, SearchCriteria.class).orElse(criteria);
        RankingStrategy strategy = request.get(STRATEGY, RankingStrategy.class).orElse(new BestMatchStrategy());

        List<Listing> ranked = strategy.rank(repository.findMatching(criteria.toFilter()), rankBy);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(COUNT, ranked.size());
        data.put(TOTAL, repository.count());
        if (ranked.isEmpty() && !criteria.isEmpty()) {
            data.put(DIAGNOSIS, ZeroMatchAnalyzer.analyze(criteria, repository.findAll()));
        }
        return ToolResult.success(ranked, data);
    }
}
