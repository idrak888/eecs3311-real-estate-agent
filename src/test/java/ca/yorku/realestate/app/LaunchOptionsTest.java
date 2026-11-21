package ca.yorku.realestate.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaunchOptionsTest {

    @Test
    void noArgumentsStartsTheGui() {
        LaunchOptions o = LaunchOptions.parse();
        assertFalse(o.cli());
        assertFalse(o.batch());
        assertTrue(o.data().isEmpty());
    }

    @Test
    void cliFlagReadsStandardInput() {
        LaunchOptions o = LaunchOptions.parse("--cli");
        assertTrue(o.cli());
        assertFalse(o.batch());
    }

    @Test
    void scriptImpliesCliAndBatch() {
        LaunchOptions o = LaunchOptions.parse("--script", "scripts/demo-commands.txt");
        assertTrue(o.cli());
        assertTrue(o.batch());
        assertEquals(new File("scripts/demo-commands.txt"), o.script().orElseThrow());
        assertEquals(new File("x.txt"), LaunchOptions.parse("-f", "x.txt").script().orElseThrow());
    }

    @Test
    void commandsAreRepeatableAndKeepOrder() {
        LaunchOptions o = LaunchOptions.parse("-c", "show L020", "--command", "search --beds 3");
        assertTrue(o.cli());
        assertEquals(List.of("show L020", "search --beds 3"), o.commands());
    }

    @Test
    void dataWorksWithEveryMode() {
        assertEquals(new File("a.csv"), LaunchOptions.parse("--data", "a.csv").data().orElseThrow());
        LaunchOptions o = LaunchOptions.parse("--cli", "--data", "a.csv");
        assertTrue(o.cli());
        assertEquals(new File("a.csv"), o.data().orElseThrow());
    }

    @Test
    void rejectsUnknownOptionsAndMissingValues() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse("--gui"));
        assertTrue(e.getMessage().contains("--gui"));
        assertTrue(e.getMessage().contains("Usage"));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--script"));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--data", "--cli"));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("-c"));
    }

    @Test
    void scriptAndCommandTogetherAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse("--script", "a.txt", "-c", "show L020"));
    }

    @Test
    void standaloneTaskRunMatchesTheInstructorExample() {
        LaunchOptions o = LaunchOptions.parse("-LLMAPI", "-input", "task.txt");
        assertTrue(o.cli());
        assertTrue(o.batch());
        assertTrue(o.requireLlm());
        assertEquals(new File("task.txt"), o.input().orElseThrow());
    }

    @Test
    void optionsAcceptOneOrTwoDashesAndAnyCase() {
        LaunchOptions o = LaunchOptions.parse("--llmapi", "anthropic", "--INPUT", "t.txt", "-Output", "out.txt",
                "-model", "claude-x", "-cli");
        assertTrue(o.requireLlm());
        assertEquals(new File("t.txt"), o.input().orElseThrow());
        assertEquals(new File("out.txt"), o.output().orElseThrow());
        assertEquals("claude-x", o.model().orElseThrow());
        assertTrue(LaunchOptions.parse("-script", "s.txt").script().isPresent());
        assertTrue(LaunchOptions.parse("-help").help());
    }

    @Test
    void unsupportedLlmProviderIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse("-LLMAPI", "openai", "-input", "t.txt"));
        assertTrue(e.getMessage().contains("anthropic"));
    }

    @Test
    void onlyOneBatchModeAtATime() {
        assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse("-input", "t.txt", "-script", "s.txt"));
        assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse("-input", "t.txt", "-c", "show L020"));
    }

    @Test
    void taskFileTurnsAreBlankLineSeparatedBlocks() {
        List<String> turns = LaunchOptions.taskTurns(List.of(
                "# my task",
                "Find a 3 bedroom house",
                "  under 1.5M with a garage.",
                "",
                "",
                "Compare the top two.",
                "# trailing comment"));
        assertEquals(List.of("Find a 3 bedroom house under 1.5M with a garage.", "Compare the top two."), turns);
        assertTrue(LaunchOptions.taskTurns(List.of("# only a comment", " ")).isEmpty());
    }
}
