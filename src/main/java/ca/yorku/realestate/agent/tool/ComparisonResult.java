package ca.yorku.realestate.agent.tool;

import ca.yorku.realestate.domain.Listing;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Attribute-by-attribute comparison matrix for two to four listings (F08).
 *
 * <p>Each attribute row holds one value per listing, in listing order. A {@code null} value
 * means the attribute is unavailable for that listing (e.g. unknown square footage); it is
 * shown as "unavailable" and excluded from leader determination. A numeric row's leader is the
 * listing with the best value; on a tie for best, the row has no leader.
 */
public final class ComparisonResult {

    /** Shown in place of a missing value. */
    public static final String UNAVAILABLE = "unavailable";

    private final List<Listing> listings;
    private final Map<String, List<Object>> attributes;
    private final Map<String, String> leaders;
    private final Set<String> numericAttributes;

    ComparisonResult(List<Listing> listings, Map<String, List<Object>> attributes,
                     Map<String, String> leaders, Set<String> numericAttributes) {
        this.listings = List.copyOf(listings);
        Map<String, List<Object>> copy = new LinkedHashMap<>();
        attributes.forEach((k, v) -> copy.put(k, Collections.unmodifiableList(new ArrayList<>(v))));
        this.attributes = Collections.unmodifiableMap(copy);
        this.leaders = Collections.unmodifiableMap(new LinkedHashMap<>(leaders));
        this.numericAttributes = Set.copyOf(numericAttributes);
    }

    public List<Listing> getListings() {
        return listings;
    }

    /** Attribute names in display order. */
    public List<String> getAttributeNames() {
        return new ArrayList<>(attributes.keySet());
    }

    /** Values for an attribute, one per listing; null entries are unavailable. */
    public List<Object> getAttribute(String name) {
        List<Object> values = attributes.get(name);
        if (values == null) {
            throw new IllegalArgumentException("Unknown attribute: " + name);
        }
        return values;
    }

    /** ID of the listing leading this attribute, or null when tied, non-numeric or unavailable. */
    public String getLeader(String name) {
        return leaders.get(name);
    }

    public Map<String, String> getLeaders() {
        return leaders;
    }

    public boolean isNumeric(String name) {
        return numericAttributes.contains(name);
    }

    /** Formats one cell for display. */
    public static String format(String attribute, Object value) {
        if (value == null) {
            return UNAVAILABLE;
        }
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            boolean money = attribute.toLowerCase(Locale.ROOT).contains("price")
                    || attribute.toLowerCase(Locale.ROOT).contains("payment");
            return money ? String.format(Locale.ROOT, "$%,.0f", d) : String.format(Locale.ROOT, "%,.1f", d);
        }
        if (value instanceof List<?>) {
            List<?> l = (List<?>) value;
            return l.isEmpty() ? "none" : String.join(", ", l.stream().map(Object::toString).toList());
        }
        return value.toString();
    }

    /** Plain-text table with leaders marked "*", used for the CLI and the explanation prompt. */
    public String toTable() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%-18s", "Attribute"));
        for (Listing l : listings) {
            sb.append(String.format(Locale.ROOT, " | %-22s", l.getId()));
        }
        sb.append('\n');
        for (String name : attributes.keySet()) {
            sb.append(String.format(Locale.ROOT, "%-18s", name));
            List<Object> values = attributes.get(name);
            for (int i = 0; i < listings.size(); i++) {
                String cell = format(name, values.get(i));
                if (listings.get(i).getId().equals(leaders.get(name))) {
                    cell = cell + " *";
                }
                sb.append(String.format(Locale.ROOT, " | %-22s", cell));
            }
            sb.append('\n');
        }
        sb.append("* = best in row\n");
        return sb.toString();
    }
}
