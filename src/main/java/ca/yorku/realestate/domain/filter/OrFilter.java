package ca.yorku.realestate.domain.filter;

import ca.yorku.realestate.domain.Listing;

/** Composite: matches when at least one child matches. With no children it matches nothing. */
public class OrFilter extends CompositeFilter {

    public OrFilter(Filter... children) {
        super(children);
    }

    @Override
    public boolean matches(Listing listing) {
        for (Filter child : getChildren()) {
            if (child.matches(listing)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String describe() {
        return join("OR");
    }

    @Override
    protected String emptyDescription() {
        return "no listings";
    }
}
