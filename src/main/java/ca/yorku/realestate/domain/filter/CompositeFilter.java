package ca.yorku.realestate.domain.filter;

import java.util.ArrayList;
import java.util.List;

/** Shared child management for the two composite filters. */
public abstract class CompositeFilter implements Filter {

    private final List<Filter> children = new ArrayList<>();

    protected CompositeFilter(Filter... initial) {
        for (Filter f : initial) {
            if (f == null) {
                throw new IllegalArgumentException("child filter must not be null");
            }
            children.add(f);
        }
    }

    public void add(Filter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("child filter must not be null");
        }
        children.add(filter);
    }

    public List<Filter> getChildren() {
        return List.copyOf(children);
    }

    protected String join(String operator) {
        if (children.isEmpty()) {
            return emptyDescription();
        }
        List<String> parts = new ArrayList<>();
        for (Filter child : children) {
            String d = child.describe();
            parts.add(child instanceof CompositeFilter && children.size() > 1 ? "(" + d + ")" : d);
        }
        return String.join(" " + operator + " ", parts);
    }

    protected abstract String emptyDescription();
}
