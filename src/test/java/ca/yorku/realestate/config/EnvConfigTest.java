package ca.yorku.realestate.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvConfigTest {

    @Test
    void parsesKeyValuesCommentsQuotesAndExport() {
        Map<String, String> v = EnvConfig.parse(List.of(
                "# comment", "", "LLM_API_KEY=sk-123", "LLM_MODEL = \"claude-x\"",
                "export OTHER='quoted value'", "not a pair", "=novalue"));
        assertEquals("sk-123", v.get("LLM_API_KEY"));
        assertEquals("claude-x", v.get("LLM_MODEL"));
        assertEquals("quoted value", v.get("OTHER"));
        assertEquals(3, v.size());
    }

    @Test
    void placeholderKeyCountsAsUnset() {
        EnvConfig c = new EnvConfig(Map.of("LLM_API_KEY", "YOUR_LOCAL_API_KEY_HERE"), Map.of());
        assertTrue(c.apiKey().isEmpty());
    }

    @Test
    void fileValueWinsOverEnvironment() {
        EnvConfig c = new EnvConfig(Map.of("LLM_API_KEY", "from-file"), Map.of("LLM_API_KEY", "from-env"));
        assertEquals("from-file", c.apiKey().orElseThrow());
    }

    @Test
    void environmentUsedWhenFileLacksKey() {
        EnvConfig c = new EnvConfig(Map.of(), Map.of("LLM_API_KEY", "from-env"));
        assertEquals("from-env", c.apiKey().orElseThrow());
    }

    @Test
    void modelDefaultsWhenUnset() {
        assertEquals(EnvConfig.DEFAULT_MODEL, new EnvConfig(Map.of(), Map.of()).model());
    }

    @Test
    void loadsFromFileAndToleratesMissingFile() throws IOException {
        Path f = Files.createTempFile("test", ".env");
        f.toFile().deleteOnExit();
        Files.writeString(f, "LLM_MODEL=claude-test\n");
        assertEquals("claude-test", EnvConfig.load(f).model());
        EnvConfig missing = EnvConfig.load(f.resolveSibling("definitely-missing.env"));
        assertTrue(missing.get("EECS3311_UNSET_TEST_KEY").isEmpty());
    }

    @Test
    void exampleFileContainsOnlyPlaceholders() throws IOException {
        Path example = Path.of(".env.example");
        if (Files.exists(example)) {
            EnvConfig c = EnvConfig.load(example);
            assertTrue(c.get(EnvConfig.API_KEY).isEmpty(), ".env.example must not contain a real key");
        }
    }
}
