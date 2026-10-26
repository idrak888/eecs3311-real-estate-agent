package ca.yorku.realestate.domain.filter;

import ca.yorku.realestate.domain.Listing;

/**
 * Composite: matches when every child matches. With no children it matches everything, so
 * clearing all criteria returns the full listing set (F03 alternative case).
 */
public class AndFilter extends CompositeFilter {

    public AndFilter(Filter... children) {
        super(children);
    }

    @Override
    public boolean matches(Listing listing) {
        for (Filter child : getChildren()) {
            if (!child.matches(listing)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String describe() {
        return join("AND");
    }

    @Override
    protected String emptyDescription() {
        return "all listings";
    }
}
