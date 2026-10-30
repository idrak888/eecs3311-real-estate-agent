package ca.yorku.realestate.service;

/** No listing has the requested ID. The message is suitable for showing to the user. */
public class NoSuchListingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String listingId;

    public NoSuchListingException(String listingId) {
        super("No listing with ID " + listingId);
        this.listingId = listingId;
    }

    public String getListingId() {
        return listingId;
    }
}
