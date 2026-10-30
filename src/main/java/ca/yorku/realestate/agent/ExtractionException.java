package ca.yorku.realestate.agent;

/**
 * The model's reply could not be turned into search criteria, even after one stricter retry.
 * The message asks the user to rephrase and is safe to display.
 */
public class ExtractionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
