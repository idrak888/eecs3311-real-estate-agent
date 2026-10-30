package ca.yorku.realestate.domain.shortlist;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered set of shortlisted listing IDs (F09). Receiver in the Command pattern.
 *
 * <p>Stores IDs rather than Listing objects so it can be persisted and restored independently
 * of the listing data. Adding an ID that is already present is a no-op, not a duplicate.
 */
public final class Shortlist {

    private final List<String> entries = new ArrayList<>();

    public Shortlist() {
    }

    /** Creates a shortlist with the given IDs, skipping blanks and duplicates. */
    public Shortlist(List<String> ids) {
        if (ids != null) {
            for (String id : ids) {
                add(id);
            }
        }
    }

    /**
     * Appends the ID if absent.
     *
     * @return true if it was added, false if it was already shortlisted
     * @throws IllegalArgumentException if the ID is blank
     */
    public boolean add(String id) {
        String key = normalize(id);
        if (entries.contains(key)) {
            return false;
        }
        entries.add(key);
        return true;
    }

    /** Inserts at a position; used by undo to restore a removed entry where it was. */
    void insert(int index, String id) {
        String key = normalize(id);
        if (!entries.contains(key)) {
            entries.add(Math.max(0, Math.min(index, entries.size())), key);
        }
    }

    /** @return true if the ID was present and removed */
    public boolean remove(String id) {
        return entries.remove(normalize(id));
    }

    public boolean contains(String id) {
        return id != null && !id.isBlank() && entries.contains(id.trim());
    }

    /** Position of the ID, or -1. */
    public int indexOf(String id) {
        return id == null ? -1 : entries.indexOf(id.trim());
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** IDs in the order they were added. */
    public List<String> getEntries() {
        return List.copyOf(entries);
    }

    private static String normalize(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Listing ID is required");
        }
        return id.trim();
    }
}
