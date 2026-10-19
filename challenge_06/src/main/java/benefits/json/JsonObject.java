package benefits.json;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Typed, path-aware view over a parsed JSON object. Every error names the exact field,
 * e.g. {@code policy.benefits[1].copay.value: must be between 0 and 100}.
 */
public final class JsonObject {

    private final Map<?, ?> map;
    private final String path;

    private JsonObject(Map<?, ?> map, String path) {
        this.map = map;
        this.path = path;
    }

    public static JsonObject of(Object value, String path) {
        if (!(value instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException(path + ": must be a JSON object");
        }
        return new JsonObject(m, path);
    }

    public String path() {
        return path;
    }

    public String path(String key) {
        return path + "." + key;
    }

    public boolean has(String key) {
        return map.get(key) != null;
    }

    public String requiredString(String key) {
        Object v = map.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw error(key, "is required and must be a non-empty string");
        }
        return s.strip();
    }

    public String optionalString(String key, String defaultValue) {
        return has(key) ? requiredString(key) : defaultValue;
    }

    public BigDecimal requiredDecimal(String key) {
        Object v = map.get(key);
        if (!(v instanceof BigDecimal d)) {
            throw error(key, "is required and must be a number");
        }
        return d;
    }

    /** Returns null when the key is absent. */
    public BigDecimal optionalDecimal(String key) {
        return has(key) ? requiredDecimal(key) : null;
    }

    /** Returns null when the key is absent. */
    public Integer optionalInt(String key) {
        if (!has(key)) {
            return null;
        }
        BigDecimal d = requiredDecimal(key);
        try {
            return d.intValueExact();
        } catch (ArithmeticException e) {
            throw error(key, "must be a whole number");
        }
    }

    public LocalDate requiredDate(String key) {
        String s = requiredString(key);
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw error(key, "must be an ISO date (YYYY-MM-DD), got \"" + s + "\"");
        }
    }

    /** Returns null when the key is absent. */
    public JsonObject optionalObject(String key) {
        return has(key) ? of(map.get(key), path(key)) : null;
    }

    public List<JsonObject> objectList(String key, boolean required) {
        if (!has(key)) {
            if (required) {
                throw error(key, "is required");
            }
            return List.of();
        }
        if (!(map.get(key) instanceof List<?> list)) {
            throw error(key, "must be an array");
        }
        List<JsonObject> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            result.add(of(list.get(i), path(key) + "[" + i + "]"));
        }
        return result;
    }

    public List<String> stringList(String key) {
        if (!has(key)) {
            return List.of();
        }
        if (!(map.get(key) instanceof List<?> list) || !list.stream().allMatch(x -> x instanceof String)) {
            throw error(key, "must be an array of strings");
        }
        return list.stream().map(x -> ((String) x).strip()).toList();
    }

    public IllegalArgumentException error(String key, String message) {
        return new IllegalArgumentException(path(key) + ": " + message);
    }
}
