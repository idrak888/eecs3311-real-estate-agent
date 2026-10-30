package ca.yorku.realestate.agent.llm;

/**
 * The language model could not be used: not configured, unreachable, timed out, or returned an
 * error. Messages never contain the API key.
 */
public class LLMException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LLMException(String message) {
        super(message);
    }

    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
