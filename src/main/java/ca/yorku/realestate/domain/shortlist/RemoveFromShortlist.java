package ca.yorku.realestate.domain.shortlist;

/**
 * Concrete command: remove a listing from the shortlist. Undo restores it at its original
 * position, so the shortlist order survives an undo.
 */
public class RemoveFromShortlist implements ShortlistCommand {

    private final Shortlist shortlist;
    private final String listingId;
    private int removedIndex = -1;

    public RemoveFromShortlist(Shortlist shortlist, String listingId) {
        if (shortlist == null) {
            throw new IllegalArgumentException("shortlist is required");
        }
        if (listingId == null || listingId.isBlank()) {
            throw new IllegalArgumentException("Listing ID is required");
        }
        this.shortlist = shortlist;
        this.listingId = listingId.trim();
    }

    @Override
    public void execute() {
        int index = shortlist.indexOf(listingId);
        if (index >= 0) {
            shortlist.remove(listingId);
        }
        removedIndex = index;
    }

    @Override
    public void undo() {
        if (removedIndex >= 0) {
            shortlist.insert(removedIndex, listingId);
            removedIndex = -1;
        }
    }

    @Override
    public boolean changedState() {
        return removedIndex >= 0;
    }

    @Override
    public String getDescription() {
        return "Remove " + listingId + " from shortlist";
    }

    public String getListingId() {
        return listingId;
    }
}
