package ca.yorku.realestate.agent.tool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Named parameters for one tool call. Values arrive either from the agent's code (typed
 * objects such as SearchCriteria) or from the model's JSON (strings and numbers), so the typed
 * getters accept both forms and report a readable error for anything else.
 */
public final class ToolRequest {

    private final Map<String, Object> parameters;

    private ToolRequest(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public static ToolRequest of(Map<String, ?> parameters) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (parameters != null) {
            parameters.forEach((k, v) -> {
                if (k != null && v != null) {
                    copy.put(k, v);
                }
            });
        }
        return new ToolRequest(copy);
    }

    public static ToolRequest empty() {
        return of(Map.of());
    }

    /** Returns a new request with one more parameter; a null value removes it. */
    public ToolRequest with(String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(parameters);
        if (value == null) {
            copy.remove(key);
        } else {
            copy.put(key, value);
        }
        return new ToolRequest(copy);
    }

    public Object get(String key) {
        return parameters.get(key);
    }

    public boolean has(String key) {
        return parameters.containsKey(key);
    }

    public Map<String, Object> asMap() {
        return Map.copyOf(parameters);
    }

    /** The value if it has the given type. */
    public <T> Optional<T> get(String key, Class<T> type) {
        Object v = parameters.get(key);
        return type.isInstance(v) ? Optional.of(type.cast(v)) : Optional.empty();
    }

    public Optional<String> getString(String key) {
        Object v = parameters.get(key);
        if (v == null) {
            return Optional.empty();
        }
        String s = v.toString().trim();
        return s.isEmpty() ? Optional.empty() : Optional.of(s);
    }

    /** @throws IllegalArgumentException if present but not a number */
    public Optional<Double> getDouble(String key) {
        Object v = parameters.get(key);
        if (v == null) {
            return Optional.empty();
        }
        if (v instanceof Number) {
            return Optional.of(((Number) v).doubleValue());
        }
        try {
            return Optional.of(Double.parseDouble(v.toString().trim().replace(",", "")));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number (was '" + v + "')");
        }
    }

    /** @throws IllegalArgumentException if present but not a whole number */
    public Optional<Integer> getInt(String key) {
        Optional<Double> d = getDouble(key);
        if (d.isPresent() && d.get() != Math.rint(d.get())) {
            throw new IllegalArgumentException(key + " must be a whole number (was " + d.get() + ")");
        }
        return d.map(Double::intValue);
    }

    /** A list parameter as strings; a single value becomes a one-element list. */
    public List<String> getStringList(String key) {
        Object v = parameters.get(key);
        List<String> out = new ArrayList<>();
        if (v instanceof Collection<?>) {
            for (Object item : (Collection<?>) v) {
                if (item != null && !item.toString().isBlank()) {
                    out.add(item.toString().trim());
                }
            }
        } else if (v != null && !v.toString().isBlank()) {
            out.add(v.toString().trim());
        }
        return out;
    }

    @Override
    public String toString() {
        return "ToolRequest" + parameters;
    }
}
