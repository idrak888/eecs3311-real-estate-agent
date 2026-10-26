package ca.yorku.realestate.persistence;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.filter.Filter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory store of listings, loaded once from the local data file at startup.
 *
 * <p>Listings are kept in load order so that results are reproducible run to run. IDs are
 * unique: if the source data repeats an ID, the first occurrence wins and the repeat is
 * reported through {@link #getDuplicateIds()}.
 */
public class ListingRepository {

    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private final List<String> duplicateIds = new ArrayList<>();

    /** Replaces the repository contents with the given listings. */
    public void load(List<Listing> newListings) {
        listings.clear();
        duplicateIds.clear();
        if (newListings == null) {
            return;
        }
        for (Listing listing : newListings) {
            if (listings.putIfAbsent(listing.getId(), listing) != null) {
                duplicateIds.add(listing.getId());
            }
        }
    }

    /** All listings, in load order. */
    public List<Listing> findAll() {
        return List.copyOf(listings.values());
    }

    /** Looks up a listing by ID; surrounding whitespace in the ID is ignored. */
    public Optional<Listing> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(listings.get(id.trim()));
    }

    /**
     * Listings satisfying the filter, in load order. The repository calls {@code matches} on the
     * root only; a composite filter recurses into its own children.
     *
     * @throws IllegalArgumentException if the filter is null
     */
    public List<Listing> findMatching(Filter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("filter must not be null");
        }
        List<Listing> result = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (filter.matches(listing)) {
                result.add(listing);
            }
        }
        return result;
    }

    public int count() {
        return listings.size();
    }

    /** IDs that appeared more than once in the last {@link #load}, in the order encountered. */
    public List<String> getDuplicateIds() {
        return Collections.unmodifiableList(duplicateIds);
    }
}
