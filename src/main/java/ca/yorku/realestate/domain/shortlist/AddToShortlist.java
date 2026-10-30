package ca.yorku.realestate.domain.shortlist;

/** Concrete command: shortlist a listing. Adding one already present changes nothing. */
public class AddToShortlist implements ShortlistCommand {

    private final Shortlist shortlist;
    private final String listingId;
    private boolean added;

    public AddToShortlist(Shortlist shortlist, String listingId) {
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
        added = shortlist.add(listingId);
    }

    @Override
    public void undo() {
        if (added) {
            shortlist.remove(listingId);
            added = false;
        }
    }

    @Override
    public boolean changedState() {
        return added;
    }

    @Override
    public String getDescription() {
        return "Add " + listingId + " to shortlist";
    }

    public String getListingId() {
        return listingId;
    }
}
