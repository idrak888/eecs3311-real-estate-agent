package ca.yorku.realestate.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.domain.finance.FinancialProfile;
import ca.yorku.realestate.domain.shortlist.Shortlist;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreferenceStoreTest {

    private static Path tempDir() throws IOException {
        Path dir = Files.createTempDirectory("prefs");
        dir.toFile().deleteOnExit();
        return dir;
    }

    @Test
    void missingFilesLoadAsEmpty() throws IOException {
        PreferenceStore store = new PreferenceStore(tempDir().resolve("not-yet-created"));
        assertTrue(store.loadShortlist().isEmpty());
        assertTrue(store.loadProfile().isEmpty());
    }

    @Test
    void shortlistRoundTripsInOrder() throws IOException {
        PreferenceStore store = new PreferenceStore(tempDir());
        store.saveShortlist(new Shortlist(List.of("L9", "L2", "L5")));
        assertEquals(List.of("L9", "L2", "L5"), store.loadShortlist().getEntries());
    }

    @Test
    void saveCreatesDirectoryAndLeavesNoTempFile() throws IOException {
        Path dir = tempDir().resolve("nested/user");
        PreferenceStore store = new PreferenceStore(dir);
        store.saveShortlist(new Shortlist(List.of("A")));
        assertTrue(Files.exists(dir.resolve(PreferenceStore.SHORTLIST_FILE)));
        assertFalse(Files.exists(dir.resolve(PreferenceStore.SHORTLIST_FILE + ".tmp")));
    }

    @Test
    void saveOverwritesPreviousShortlist() throws IOException {
        PreferenceStore store = new PreferenceStore(tempDir());
        store.saveShortlist(new Shortlist(List.of("A", "B")));
        store.saveShortlist(new Shortlist(List.of("C")));
        assertEquals(List.of("C"), store.loadShortlist().getEntries());
    }

    @Test
    void profileRoundTrips() throws IOException {
        PreferenceStore store = new PreferenceStore(tempDir());
        store.saveProfile(new FinancialProfile(150_000, 750, 120_000));
        FinancialProfile p = store.loadProfile().orElseThrow();
        assertEquals(150_000, p.getAnnualIncome(), 1e-9);
        assertEquals(750, p.getMonthlyDebt(), 1e-9);
        assertEquals(120_000, p.getDownPayment(), 1e-9);
    }

    @Test
    void corruptShortlistThrowsInsteadOfSilentlyDiscarding() throws IOException {
        Path dir = tempDir();
        Files.writeString(dir.resolve(PreferenceStore.SHORTLIST_FILE), "{ not json");
        IOException e = assertThrows(IOException.class, () -> new PreferenceStore(dir).loadShortlist());
        assertTrue(e.getMessage().contains("not valid JSON"));
    }

    @Test
    void shortlistWithoutEntriesArrayIsRejected() throws IOException {
        Path dir = tempDir();
        Files.writeString(dir.resolve(PreferenceStore.SHORTLIST_FILE), "{\"entries\": 3}");
        assertThrows(IOException.class, () -> new PreferenceStore(dir).loadShortlist());
    }

    @Test
    void invalidProfileValuesAreRejected() throws IOException {
        Path dir = tempDir();
        Files.writeString(dir.resolve(PreferenceStore.PROFILE_FILE),
                "{\"annualIncome\": -5, \"monthlyDebt\": 0, \"downPayment\": 0}");
        IOException e = assertThrows(IOException.class, () -> new PreferenceStore(dir).loadProfile());
        assertTrue(e.getMessage().contains("invalid"));
    }

    @Test
    void profileMissingFieldIsRejected() throws IOException {
        Path dir = tempDir();
        Files.writeString(dir.resolve(PreferenceStore.PROFILE_FILE), "{\"annualIncome\": 1}");
        assertThrows(IOException.class, () -> new PreferenceStore(dir).loadProfile());
    }

    @Test
    void unwritableDirectoryFailsSave() throws IOException {
        Path file = Files.createTempFile("not-a-dir", ".txt");
        file.toFile().deleteOnExit();
        PreferenceStore store = new PreferenceStore(file);
        assertThrows(IOException.class, () -> store.saveShortlist(new Shortlist(List.of("A"))));
    }

    @Test
    void defaultDirectoryIsGitIgnoredUserData() {
        assertEquals(Path.of("data/user"), new PreferenceStore().getDirectory());
    }
}
