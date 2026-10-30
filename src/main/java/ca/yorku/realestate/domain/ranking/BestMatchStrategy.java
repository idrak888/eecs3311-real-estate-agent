package ca.yorku.realestate.domain.ranking;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import ca.yorku.realestate.domain.SearchCriteria;
import java.util.Locale;
import java.util.Set;

/**
 * Closest fit to the active criteria first.
 *
 * <p>Each active criterion contributes a satisfaction value between 0 and 1, and the score is
 * their average. A criterion that is fully met scores 1; a near miss scores partially (e.g. two
 * bedrooms against a minimum of three scores 2/3, a price 10% over budget scores 0.9, two of
 * three required features score 2/3). Partial credit matters when ranking a relaxed or
 * unfiltered list, where not every listing meets every criterion.
 *
 * <p>With no active criteria every listing scores 0, so the order falls back to listing ID.
 */
public class BestMatchStrategy implements RankingStrategy {

    @Override
    public double score(Listing listing, SearchCriteria criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return 0;
        }
        double total = 0;
        int active = 0;

        if (criteria.getMinPrice() != null || criteria.getMaxPrice() != null) {
            total += priceFit(listing.getPrice(), criteria.getMinPrice(), criteria.getMaxPrice());
            active++;
        }
        if (criteria.getMinBedrooms() != null) {
            total += minimumFit(listing.getBedrooms(), criteria.getMinBedrooms());
            active++;
        }
        if (criteria.getMinBathrooms() != null) {
            total += minimumFit(listing.getBathrooms(), criteria.getMinBathrooms());
            active++;
        }
        if (!criteria.getTypes().isEmpty()) {
            total += typeFit(listing.getType(), criteria.getTypes());
            active++;
        }
        if (!criteria.getNeighborhoods().isEmpty()) {
            total += neighborhoodFit(listing.getNeighborhood(), criteria.getNeighborhoods());
            active++;
        }
        if (!criteria.getRequiredFeatures().isEmpty()) {
            total += featureFit(listing, criteria.getRequiredFeatures());
            active++;
        }
        return total / active;
    }

    /** 1 inside the range; otherwise falls off linearly with relative distance from the bound. */
    static double priceFit(double price, Double min, Double max) {
        if (max != null && price > max) {
            return max <= 0 ? 0 : clamp(1 - (price - max) / max);
        }
        if (min != null && price < min) {
            return min <= 0 ? 0 : clamp(1 - (min - price) / min);
        }
        return 1;
    }

    static double minimumFit(int actual, int minimum) {
        if (minimum <= 0 || actual >= minimum) {
            return 1;
        }
        return (double) actual / minimum;
    }

    private static double typeFit(PropertyType type, Set<PropertyType> wanted) {
        return wanted.contains(type) ? 1 : 0;
    }

    private static double neighborhoodFit(String neighborhood, Set<String> wanted) {
        String n = neighborhood.trim().toLowerCase(Locale.ROOT);
        for (String w : wanted) {
            if (w.trim().toLowerCase(Locale.ROOT).equals(n)) {
                return 1;
            }
        }
        return 0;
    }

    private static double featureFit(Listing listing, Set<String> required) {
        int present = 0;
        for (String f : required) {
            if (listing.hasFeature(f)) {
                present++;
            }
        }
        return (double) present / required.size();
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    @Override
    public String getName() {
        return "Best match";
    }
}
