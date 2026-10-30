package ca.yorku.realestate.domain.shortlist;

/**
 * Command interface for shortlist actions (F09). Each command knows how to reverse itself,
 * so {@link CommandInvoker} can undo without knowing what any command does.
 */
public interface ShortlistCommand {

    void execute();

    /** Reverses exactly the effect of the last {@link #execute()}. */
    void undo();

    /** Short description for confirmations and the Edit menu, e.g. "Add L012 to shortlist". */
    String getDescription();

    /**
     * Whether the last execute changed the shortlist. A command that changed nothing (adding an
     * entry already present) is not kept in the undo history.
     */
    boolean changedState();
}
