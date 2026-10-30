package ca.yorku.realestate.domain.shortlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShortlistCommandTest {

    // ---- Shortlist ----

    @Test
    void addKeepsOrderAndRejectsDuplicates() {
        Shortlist s = new Shortlist();
        assertTrue(s.add("L1"));
        assertTrue(s.add("L2"));
        assertFalse(s.add(" L1 "));
        assertEquals(List.of("L1", "L2"), s.getEntries());
    }

    @Test
    void removeContainsAndSize() {
        Shortlist s = new Shortlist(List.of("L1", "L2"));
        assertTrue(s.contains("L2"));
        assertTrue(s.remove("L2"));
        assertFalse(s.remove("L2"));
        assertEquals(1, s.size());
        assertFalse(s.contains(null));
    }

    @Test
    void constructorSkipsDuplicatesButRejectsBlank() {
        assertEquals(List.of("A", "B"), new Shortlist(List.of("A", "B", "A")).getEntries());
        assertThrows(IllegalArgumentException.class, () -> new Shortlist(Arrays.asList("A", " ")));
        assertThrows(IllegalArgumentException.class, () -> new Shortlist().add(null));
    }

    @Test
    void entriesAreACopy() {
        Shortlist s = new Shortlist(List.of("A"));
        List<String> snapshot = s.getEntries();
        s.add("B");
        assertEquals(1, snapshot.size());
    }

    // ---- commands + invoker ----

    @Test
    void addThenUndoRemoves() {
        Shortlist s = new Shortlist();
        CommandInvoker invoker = new CommandInvoker();
        invoker.execute(new AddToShortlist(s, "L7"));
        assertTrue(s.contains("L7"));
        assertTrue(invoker.canUndo());
        assertEquals("Add L7 to shortlist", invoker.undo().orElseThrow().getDescription());
        assertTrue(s.isEmpty());
        assertFalse(invoker.canUndo());
    }

    @Test
    void duplicateAddIsNoOpAndNotRecorded() {
        Shortlist s = new Shortlist(List.of("L7"));
        CommandInvoker invoker = new CommandInvoker();
        AddToShortlist dup = new AddToShortlist(s, "L7");
        invoker.execute(dup);
        assertFalse(dup.changedState());
        assertFalse(invoker.canUndo());
        assertEquals(List.of("L7"), s.getEntries());
    }

    @Test
    void removeThenUndoRestoresOriginalPosition() {
        Shortlist s = new Shortlist(List.of("A", "B", "C"));
        CommandInvoker invoker = new CommandInvoker();
        invoker.execute(new RemoveFromShortlist(s, "B"));
        assertEquals(List.of("A", "C"), s.getEntries());
        invoker.undo();
        assertEquals(List.of("A", "B", "C"), s.getEntries());
    }

    @Test
    void removingAbsentIdIsNotRecorded() {
        Shortlist s = new Shortlist(List.of("A"));
        CommandInvoker invoker = new CommandInvoker();
        invoker.execute(new RemoveFromShortlist(s, "Z"));
        assertFalse(invoker.canUndo());
    }

    @Test
    void undoOnEmptyHistoryReportsNothing() {
        assertTrue(new CommandInvoker().undo().isEmpty());
        assertTrue(new CommandInvoker().peekUndoDescription().isEmpty());
    }

    @Test
    void undoIsLastInFirstOut() {
        Shortlist s = new Shortlist();
        CommandInvoker invoker = new CommandInvoker();
        invoker.execute(new AddToShortlist(s, "A"));
        invoker.execute(new AddToShortlist(s, "B"));
        invoker.execute(new RemoveFromShortlist(s, "A"));
        assertEquals("Remove A from shortlist", invoker.peekUndoDescription().orElseThrow());

        invoker.undo();
        assertEquals(List.of("A", "B"), s.getEntries());
        invoker.undo();
        assertEquals(List.of("A"), s.getEntries());
        invoker.undo();
        assertTrue(s.isEmpty());
        assertFalse(invoker.canUndo());
    }

    @Test
    void historyIsCappedAtMax() {
        Shortlist s = new Shortlist();
        CommandInvoker invoker = new CommandInvoker();
        for (int i = 0; i < CommandInvoker.MAX_HISTORY + 5; i++) {
            invoker.execute(new AddToShortlist(s, "L" + i));
        }
        int undone = 0;
        while (invoker.undo().isPresent()) {
            undone++;
        }
        assertEquals(CommandInvoker.MAX_HISTORY, undone);
        assertEquals(5, s.size());
    }

    @Test
    void commandsValidateArguments() {
        Shortlist s = new Shortlist();
        assertThrows(IllegalArgumentException.class, () -> new AddToShortlist(null, "A"));
        assertThrows(IllegalArgumentException.class, () -> new AddToShortlist(s, " "));
        assertThrows(IllegalArgumentException.class, () -> new RemoveFromShortlist(s, null));
        assertThrows(IllegalArgumentException.class, () -> new CommandInvoker().execute(null));
    }
}
