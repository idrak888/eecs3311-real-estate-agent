package ca.yorku.realestate.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads local configuration from a {@code .env} file, falling back to environment variables.
 *
 * <p>The real {@code .env} is git-ignored; {@code .env.example} with placeholders is committed.
 * Lines are {@code KEY=value}; blank lines and {@code #} comments are skipped, and values may be
 * wrapped in single or double quotes. A value still equal to a placeholder from
 * {@code .env.example} is treated as unset, so a copied-but-unedited file does not look like a
 * real key.
 */
public final class EnvConfig {

    public static final String API_KEY = "LLM_API_KEY";
    public static final String MODEL = "LLM_MODEL";
    public static final String DEFAULT_MODEL = "claude-sonnet-5-5";

    private static final List<String> PLACEHOLDERS = List.of("YOUR_LOCAL_API_KEY_HERE", "");

    private final Map<String, String> fileValues;
    private final Map<String, String> environment;

    EnvConfig(Map<String, String> fileValues, Map<String, String> environment) {
        this.fileValues = Map.copyOf(fileValues);
        this.environment = Map.copyOf(environment);
    }

    /** Loads {@code .env} from the working directory if present, plus the process environment. */
    public static EnvConfig load() {
        return load(Path.of(".env"));
    }

    public static EnvConfig load(Path envFile) {
        Map<String, String> values = new HashMap<>();
        if (Files.isRegularFile(envFile)) {
            try {
                values = parse(Files.readAllLines(envFile, StandardCharsets.UTF_8));
            } catch (IOException e) {
                // Unreadable .env behaves like a missing one; the key will show as not configured.
            }
        }
        return new EnvConfig(values, System.getenv());
    }

    static Map<String, String> parse(List<String> lines) {
        Map<String, String> values = new HashMap<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        return values;
    }

    /** The value from {@code .env}, else the environment; empty if unset or a placeholder. */
    public Optional<String> get(String key) {
        String v = fileValues.containsKey(key) ? fileValues.get(key) : environment.get(key);
        if (v == null || PLACEHOLDERS.contains(v.trim())) {
            return Optional.empty();
        }
        return Optional.of(v.trim());
    }

    public Optional<String> apiKey() {
        return get(API_KEY);
    }

    public String model() {
        return get(MODEL).orElse(DEFAULT_MODEL);
    }
}
