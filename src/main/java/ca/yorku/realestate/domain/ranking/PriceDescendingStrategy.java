package ca.yorku.realestate.domain.ranking;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.SearchCriteria;

/** Most expensive first. */
public class PriceDescendingStrategy implements RankingStrategy {

    @Override
    public double score(Listing listing, SearchCriteria criteria) {
        return listing.getPrice();
    }

    @Override
    public String getName() {
        return "Price (high to low)";
    }
}
