package ca.yorku.realestate.domain.filter;

import ca.yorku.realestate.domain.Listing;
import java.util.Locale;

/** Leaf: price within an inclusive range. Either bound may be open. */
public class PriceFilter implements Filter {

    private final double min;
    private final double max;

    /**
     * @param min lower bound, or {@code null} for no lower bound
     * @param max upper bound, or {@code null} for no upper bound
     * @throws IllegalArgumentException if a bound is negative or min exceeds max
     */
    public PriceFilter(Double min, Double max) {
        this.min = min == null ? 0 : min;
        this.max = max == null ? Double.POSITIVE_INFINITY : max;
        if (this.min < 0 || this.max < 0) {
            throw new IllegalArgumentException("price bounds must be non-negative");
        }
        if (this.min > this.max) {
            throw new IllegalArgumentException("minimum price " + this.min
                    + " exceeds maximum price " + this.max);
        }
    }

    @Override
    public boolean matches(Listing listing) {
        double price = listing.getPrice();
        return price >= min && price <= max;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    @Override
    public String describe() {
        if (max == Double.POSITIVE_INFINITY) {
            return String.format(Locale.ROOT, "price >= %.0f", min);
        }
        if (min == 0) {
            return String.format(Locale.ROOT, "price <= %.0f", max);
        }
        return String.format(Locale.ROOT, "price %.0f-%.0f", min, max);
    }
}
