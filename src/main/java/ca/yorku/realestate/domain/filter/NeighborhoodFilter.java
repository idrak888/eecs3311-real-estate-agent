package ca.yorku.realestate.domain.filter;

import ca.yorku.realestate.domain.Listing;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Leaf: neighborhood is one of an allowed set, compared case-insensitively.
 *
 * <p>Added in Stage 2: SearchCriteria carried neighborhoods in the Stage 1 design but no leaf
 * filter evaluated it.
 */
public class NeighborhoodFilter implements Filter {

    private final Set<String> allowed;

    public NeighborhoodFilter(Set<String> neighborhoods) {
        Set<String> normalized = new LinkedHashSet<>();
        if (neighborhoods != null) {
            for (String n : neighborhoods) {
                if (n != null && !n.isBlank()) {
                    normalized.add(normalize(n));
                }
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("at least one neighborhood is required");
        }
        this.allowed = normalized;
    }

    public NeighborhoodFilter(String neighborhood) {
        this(Set.of(neighborhood));
    }

    private static String normalize(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean matches(Listing listing) {
        return allowed.contains(normalize(listing.getNeighborhood()));
    }

    @Override
    public String describe() {
        return "neighborhood " + String.join("/", allowed);
    }
}
