package ca.yorku.realestate.domain.ranking;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.SearchCriteria;

/**
 * Lowest price per square foot first. Listings with unknown square footage sort last rather
 * than being treated as zero (F04 alternative case).
 */
public class ValuePerSqFtStrategy implements RankingStrategy {

    @Override
    public double score(Listing listing, SearchCriteria criteria) {
        if (!listing.hasSquareFeet()) {
            return Double.NEGATIVE_INFINITY;
        }
        return -listing.getPricePerSqFt();
    }

    @Override
    public String getName() {
        return "Value (price per sq ft)";
    }
}
