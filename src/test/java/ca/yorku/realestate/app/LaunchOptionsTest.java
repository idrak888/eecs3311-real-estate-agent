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
}
