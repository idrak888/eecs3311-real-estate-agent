package ca.yorku.realestate.agent.tool;

import ca.yorku.realestate.domain.Listing;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outcome of a tool call. On success it carries the listings involved and named data (e.g. a
 * MortgageResult); on failure, a user-readable error message. Listings in a result are the
 * only properties the agent is allowed to talk about.
 */
public final class ToolResult {

    private final boolean success;
    private final List<Listing> listings;
    private final Map<String, Object> data;
    private final String errorMessage;

    private ToolResult(boolean success, List<Listing> listings, Map<String, Object> data, String errorMessage) {
        this.success = success;
        this.listings = listings == null ? List.of() : List.copyOf(listings);
        this.data = data == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(data));
        this.errorMessage = errorMessage;
    }

    public static ToolResult success(List<Listing> listings, Map<String, Object> data) {
        return new ToolResult(true, listings, data, null);
    }

    public static ToolResult failure(String errorMessage) {
        return new ToolResult(false, List.of(), Map.of(), errorMessage == null ? "Tool failed" : errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public List<Listing> getListings() {
        return listings;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public <T> Optional<T> getData(String key, Class<T> type) {
        Object v = data.get(key);
        return type.isInstance(v) ? Optional.of(type.cast(v)) : Optional.empty();
    }

    /** The error message, or null on success. */
    public String getErrorMessage() {
        return errorMessage;
    }
}
