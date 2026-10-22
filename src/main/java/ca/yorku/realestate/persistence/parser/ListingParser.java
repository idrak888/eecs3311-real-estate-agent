package ca.yorku.realestate.persistence.parser;

import ca.yorku.realestate.domain.Listing;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Product interface of the Factory Method pattern: turns a listing data file into Listings.
 *
 * <p>Parsing is tolerant at the record level. A malformed record is skipped and its reason
 * recorded in {@link #getRejections()}; one bad row never aborts the whole import. Only a
 * problem with the file itself (missing, unreadable, not the expected overall structure)
 * fails the parse.
 */
public interface ListingParser {

    /**
     * Parses every record in the file.
     *
     * @return the valid listings, in file order
     * @throws IOException if the file cannot be read or its overall structure is invalid
     */
    List<Listing> parse(File file) throws IOException;

    /** Whether this parser handles files with the given extension (without the dot). */
    boolean supports(String extension);

    /**
     * Human-readable reasons for each record rejected by the most recent {@link #parse} call,
     * e.g. {@code "record 7 (id=L042): price must be positive (was -1.0)"}.
     */
    List<String> getRejections();
}
