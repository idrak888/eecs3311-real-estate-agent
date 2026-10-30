package ca.yorku.realestate.domain.shortlist;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Invoker in the Command pattern: runs commands and keeps the undo history (F09).
 *
 * <p>Commands that change nothing (adding a duplicate, removing an absent ID) are not
 * recorded, so undo never "undoes" a no-op.
 */
public class CommandInvoker {

    /** Oldest commands are dropped beyond this many. */
    public static final int MAX_HISTORY = 50;

    private final Deque<ShortlistCommand> history = new ArrayDeque<>();

    /** Executes the command and records it if it changed the shortlist. */
    public void execute(ShortlistCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        command.execute();
        if (command.changedState()) {
            history.push(command);
            if (history.size() > MAX_HISTORY) {
                history.removeLast();
            }
        }
    }

    /**
     * Undoes the most recent recorded command.
     *
     * @return the command undone, or empty if there was nothing to undo
     */
    public Optional<ShortlistCommand> undo() {
        if (history.isEmpty()) {
            return Optional.empty();
        }
        ShortlistCommand command = history.pop();
        command.undo();
        return Optional.of(command);
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    /** Description of what undo would reverse, for the Edit menu. */
    public Optional<String> peekUndoDescription() {
        return history.isEmpty() ? Optional.empty() : Optional.of(history.peek().getDescription());
    }

    public void clear() {
        history.clear();
    }
}
