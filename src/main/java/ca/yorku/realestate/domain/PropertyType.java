package ca.yorku.realestate.domain;

import java.util.Locale;

/** The kinds of property the system supports. */
public enum PropertyType {
    HOUSE,
    CONDO,
    TOWNHOUSE;

    /**
     * Parses a type name case-insensitively, tolerating surrounding whitespace.
     *
     * @throws IllegalArgumentException if the text names no supported type
     */
    public static PropertyType fromString(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Property type is missing");
        }
        try {
            return PropertyType.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown property type: " + text);
        }
    }
}
