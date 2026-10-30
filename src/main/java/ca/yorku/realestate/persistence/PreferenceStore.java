package ca.yorku.realestate.persistence;

import ca.yorku.realestate.domain.finance.FinancialProfile;
import ca.yorku.realestate.domain.shortlist.Shortlist;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persists the user's shortlist and financial profile as JSON between sessions (F07, F09).
 *
 * <p>Files live in one directory ({@value #DEFAULT_DIRECTORY} by default, which is git-ignored):
 * {@value #SHORTLIST_FILE} and {@value #PROFILE_FILE}. Writes go to a temporary file first and
 * are then moved into place, so a crash mid-write never leaves a half-written file.
 *
 * <p>A missing file is normal (first run) and loads as empty. A file that exists but cannot be
 * read or parsed throws {@link IOException}, so the caller can warn the user rather than
 * silently discarding their data.
 */
public class PreferenceStore {

    public static final String DEFAULT_DIRECTORY = "data/user";
    public static final String SHORTLIST_FILE = "shortlist.json";
    public static final String PROFILE_FILE = "profile.json";

    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    public PreferenceStore() {
        this(Path.of(DEFAULT_DIRECTORY));
    }

    public PreferenceStore(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("directory is required");
        }
        this.directory = directory;
    }

    public Path getDirectory() {
        return directory;
    }

    // ---- shortlist ----

    /** @throws IOException if the file cannot be written; the in-memory shortlist is unaffected */
    public void saveShortlist(Shortlist shortlist) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode entries = root.putArray("entries");
        shortlist.getEntries().forEach(entries::add);
        write(SHORTLIST_FILE, root);
    }

    /**
     * @return the saved shortlist, or an empty one if none has been saved
     * @throws IOException if a saved file exists but is unreadable or malformed
     */
    public Shortlist loadShortlist() throws IOException {
        Optional<JsonNode> root = read(SHORTLIST_FILE);
        if (root.isEmpty()) {
            return new Shortlist();
        }
        JsonNode entries = root.get().get("entries");
        if (entries == null || !entries.isArray()) {
            throw new IOException(SHORTLIST_FILE + " has no \"entries\" array");
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode e : entries) {
            if (e.isTextual() && !e.asText().isBlank()) {
                ids.add(e.asText());
            }
        }
        return new Shortlist(ids);
    }

    // ---- profile ----

    public void saveProfile(FinancialProfile profile) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("annualIncome", profile.getAnnualIncome());
        root.put("monthlyDebt", profile.getMonthlyDebt());
        root.put("downPayment", profile.getDownPayment());
        write(PROFILE_FILE, root);
    }

    /**
     * @return the saved profile, or empty if none has been saved (the UI then prompts for one)
     * @throws IOException if a saved file exists but is unreadable or invalid
     */
    public Optional<FinancialProfile> loadProfile() throws IOException {
        Optional<JsonNode> root = read(PROFILE_FILE);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        JsonNode n = root.get();
        try {
            return Optional.of(new FinancialProfile(number(n, "annualIncome"),
                    number(n, "monthlyDebt"), number(n, "downPayment")));
        } catch (IllegalArgumentException e) {
            throw new IOException(PROFILE_FILE + " is invalid: " + e.getMessage(), e);
        }
    }

    private static double number(JsonNode node, String field) throws IOException {
        JsonNode v = node.get(field);
        if (v == null || !v.isNumber()) {
            throw new IOException(PROFILE_FILE + " is missing numeric field \"" + field + "\"");
        }
        return v.asDouble();
    }

    // ---- io ----

    private void write(String fileName, JsonNode content) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(fileName);
        Path temp = directory.resolve(fileName + ".tmp");
        Files.writeString(temp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(content),
                StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Optional<JsonNode> read(String fileName) throws IOException {
        Path file = directory.resolve(fileName);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            JsonNode root = mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
            if (root == null || !root.isObject()) {
                throw new IOException(fileName + " does not contain a JSON object");
            }
            return Optional.of(root);
        } catch (JsonProcessingException e) {
            throw new IOException(fileName + " is not valid JSON: " + e.getOriginalMessage(), e);
        }
    }
}
