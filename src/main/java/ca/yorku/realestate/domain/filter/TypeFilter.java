package ca.yorku.realestate.domain.filter;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Leaf: property type is one of an allowed set. */
public class TypeFilter implements Filter {

    private final Set<PropertyType> allowed;

    public TypeFilter(Set<PropertyType> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            throw new IllegalArgumentException("at least one property type is required");
        }
        this.allowed = EnumSet.copyOf(allowed);
    }

    public TypeFilter(PropertyType type) {
        this(EnumSet.of(type));
    }

    @Override
    public boolean matches(Listing listing) {
        return allowed.contains(listing.getType());
    }

    public Set<PropertyType> getAllowed() {
        return EnumSet.copyOf(allowed);
    }

    @Override
    public String describe() {
        return "type " + allowed.stream().map(t -> t.name().toLowerCase(java.util.Locale.ROOT))
                .collect(Collectors.joining("/"));
    }
}
