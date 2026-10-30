package ca.yorku.realestate.service;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.domain.shortlist.Shortlist;
import java.util.List;

/**
 * Observer of {@link RealEstateService} state. The GUI and CLI both implement it, and the
 * service notifies every registered observer without knowing which frontends are attached.
 *
 * <p>Methods beyond the two in the Stage 1 design have empty defaults, so an observer
 * overrides only what it displays.
 */
public interface SearchObserver {

    /** Results were searched or re-ranked. */
    void onResultsChanged(List<Listing> results);

    /** The session criteria changed. */
    void onCriteriaChanged(SearchCriteria criteria);

    /** The shortlist changed (add, remove, undo, restore). */
    default void onShortlistChanged(Shortlist shortlist) {
    }

    /** A non-fatal problem the user should see, e.g. the shortlist could not be saved. */
    default void onWarning(String message) {
    }
}
