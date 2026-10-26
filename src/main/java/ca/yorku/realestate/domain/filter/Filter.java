package ca.yorku.realestate.domain.filter;

import ca.yorku.realestate.domain.Listing;

/**
 * Component of the Composite pattern: a condition a listing either satisfies or does not.
 *
 * <p>Leaf filters test one field. {@link AndFilter} and {@link OrFilter} combine other filters,
 * so arbitrarily nested queries such as price AND bedrooms AND (condo OR townhouse) are one
 * Filter. Callers such as {@code ListingRepository.findMatching} call {@link #matches} on the
 * root and never need to know the shape of the tree.
 */
public interface Filter {

    boolean matches(Listing listing);

    /** Short human-readable form, e.g. {@code "price 500000-850000"}. Used in summaries. */
    String describe();
}
