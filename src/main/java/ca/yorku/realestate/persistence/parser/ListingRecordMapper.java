package ca.yorku.realestate.persistence.parser;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts one raw record (field name to text value) into a validated Listing.
 *
 * <p>Both parsers reduce their format to this common shape first, so field conversion and
 * validation rules live in exactly one place regardless of file format.
 */
final class ListingRecordMapper {

    static final String ID = "id";
    static final String PRICE = "price";
    static final String BEDROOMS = "bedrooms";
    static final String BATHROOMS = "bathrooms";
    static final String SQUARE_FEET = "squareFeet";
    static final String TYPE = "type";
    static final String NEIGHBORHOOD = "neighborhood";
    static final String YEAR_BUILT = "yearBuilt";
    static final String FEATURES = "features";

    /** Separator for multiple features inside a single field. */
    static final String FEATURE_SEPARATOR = ";";

    private ListingRecordMapper() {
    }

    /**
     * @throws IllegalArgumentException with a readable reason if any field is missing or invalid
     */
    static Listing toListing(Map<String, String> record) {
        String id = required(record, ID);
        double price = parseDouble(required(record, PRICE), PRICE);
        int bedrooms = parseInt(required(record, BEDROOMS), BEDROOMS);
        int bathrooms = parseInt(required(record, BATHROOMS), BATHROOMS);
        String sqftText = record.get(SQUARE_FEET);
        int squareFeet = isBlank(sqftText)
                ? Listing.UNKNOWN_SQUARE_FEET
                : parseInt(sqftText, SQUARE_FEET);
        PropertyType type = PropertyType.fromString(required(record, TYPE));
        String neighborhood = required(record, NEIGHBORHOOD);
        int yearBuilt = parseInt(required(record, YEAR_BUILT), YEAR_BUILT);
        List<String> features = splitFeatures(record.get(FEATURES));

        return new Listing(id, price, bedrooms, bathrooms, squareFeet, type, neighborhood,
                yearBuilt, features);
    }

    /** Builds a rejection message, including the id when the record had one. */
    static String rejection(int recordNumber, Map<String, String> record, String reason) {
        String id = record == null ? null : record.get(ID);
        String label = isBlank(id)
                ? "record " + recordNumber
                : "record " + recordNumber + " (id=" + id.trim() + ")";
        return label + ": " + reason;
    }

    private static String required(Map<String, String> record, String field) {
        String value = record.get(field);
        if (isBlank(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static double parseDouble(String text, String field) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " is not a number: '" + text + "'");
        }
    }

    private static int parseInt(String text, String field) {
        String t = text.trim();
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            // Accept whole-number decimals such as "3.0" from spreadsheet exports.
            try {
                double d = Double.parseDouble(t);
                if (d == Math.rint(d) && !Double.isInfinite(d)) {
                    return (int) d;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the error below
            }
            throw new IllegalArgumentException(field + " is not a whole number: '" + text + "'");
        }
    }

    private static List<String> splitFeatures(String text) {
        List<String> out = new ArrayList<>();
        if (isBlank(text)) {
            return out;
        }
        for (String part : text.split(FEATURE_SEPARATOR)) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
