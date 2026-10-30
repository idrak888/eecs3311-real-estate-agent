package ca.yorku.realestate.domain.ranking;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.SearchCriteria;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Strategy interface for ordering search results (F04).
 *
 * <p>A strategy only supplies a score; a higher score ranks earlier. {@link #rank} does the
 * sorting identically for every strategy, breaking ties by listing ID so repeated runs give the
 * same order. Adding a ranking mode means one new class implementing {@link #score}.
 */
public interface RankingStrategy {

    /**
     * Scores one listing. Higher ranks earlier. A strategy that cannot score a listing (e.g.
     * value per square foot with unknown size) returns {@link Double#NEGATIVE_INFINITY} so it
     * sorts last.
     *
     * @param criteria the active search criteria; strategies that do not need it ignore it.
     *     May be {@code null}, which strategies treat as no criteria.
     */
    double score(Listing listing, SearchCriteria criteria);

    /** Display name for the GUI dropdown and CLI output, e.g. "Price (low to high)". */
    String getName();

    /**
     * Returns a new list sorted by descending score, then ascending ID. The input is not
     * modified.
     */
    default List<Listing> rank(List<Listing> listings, SearchCriteria criteria) {
        List<Listing> sorted = new ArrayList<>(listings);
        Comparator<Listing> byScore = Comparator.comparingDouble(l -> score(l, criteria));
        sorted.sort(byScore.reversed().thenComparing(Listing::getId));
        return sorted;
    }
}
