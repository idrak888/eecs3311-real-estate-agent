package ca.yorku.realestate.domain.filter;

import ca.yorku.realestate.domain.Listing;

/** Leaf: at least a minimum number of bedrooms. */
public class BedroomFilter implements Filter {

    private final int minimum;

    public BedroomFilter(int minimum) {
        if (minimum < 0) {
            throw new IllegalArgumentException("minimum bedrooms must be non-negative");
        }
        this.minimum = minimum;
    }

    @Override
    public boolean matches(Listing listing) {
        return listing.getBedrooms() >= minimum;
    }

    public int getMinimum() {
        return minimum;
    }

    @Override
    public String describe() {
        return "bedrooms >= " + minimum;
    }
}
