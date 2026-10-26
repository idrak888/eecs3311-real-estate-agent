package ca.yorku.realestate.domain.filter;

import ca.yorku.realestate.domain.Listing;

/**
 * Leaf: at least a minimum number of bathrooms.
 *
 * <p>Added in Stage 2: SearchCriteria carried minBathrooms in the Stage 1 design but no leaf
 * filter evaluated it.
 */
public class BathroomFilter implements Filter {

    private final int minimum;

    public BathroomFilter(int minimum) {
        if (minimum < 0) {
            throw new IllegalArgumentException("minimum bathrooms must be non-negative");
        }
        this.minimum = minimum;
    }

    @Override
    public boolean matches(Listing listing) {
        return listing.getBathrooms() >= minimum;
    }

    public int getMinimum() {
        return minimum;
    }

    @Override
    public String describe() {
        return "bathrooms >= " + minimum;
    }
}
