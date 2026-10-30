package ca.yorku.realestate.domain.ranking;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.SearchCriteria;

/** Cheapest first. */
public class PriceAscendingStrategy implements RankingStrategy {

    @Override
    public double score(Listing listing, SearchCriteria criteria) {
        return -listing.getPrice();
    }

    @Override
    public String getName() {
        return "Price (low to high)";
    }
}
