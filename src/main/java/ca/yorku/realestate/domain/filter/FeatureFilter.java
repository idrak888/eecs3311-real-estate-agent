package ca.yorku.realestate.domain.filter;

import ca.yorku.realestate.domain.Listing;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Leaf: listing has every required feature (case-insensitive). */
public class FeatureFilter implements Filter {

    private final Set<String> required;

    public FeatureFilter(Set<String> features) {
        Set<String> normalized = new LinkedHashSet<>();
        if (features != null) {
            for (String f : features) {
                if (f != null && !f.isBlank()) {
                    normalized.add(f.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("at least one feature is required");
        }
        this.required = normalized;
    }

    public FeatureFilter(String feature) {
        this(Set.of(feature));
    }

    @Override
    public boolean matches(Listing listing) {
        for (String feature : required) {
            if (!listing.hasFeature(feature)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String describe() {
        return "features " + String.join(" + ", required);
    }
}
