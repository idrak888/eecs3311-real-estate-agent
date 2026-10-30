package ca.yorku.realestate.agent.llm;

/**
 * Target interface of the Adapter pattern: the only way the agent layer reaches a language
 * model. {@link AnthropicLLMClient} adapts the live API; {@link MockLLMClient} returns fixed
 * responses so the agent layer is unit-testable offline. Swapping providers means one new
 * implementation of this interface.
 */
public interface LLMClient {

    /**
     * Sends a prompt and returns the model's text reply.
     *
     * @throws LLMException if the model cannot be reached or returns an error
     */
    String complete(String prompt);

    /**
     * Sends a prompt and asks for a reply that is a single JSON object conforming to the given
     * JSON Schema. The reply is returned as text; callers must still parse and validate it,
     * since a model can deviate from instructions.
     *
     * @throws LLMException if the model cannot be reached or returns an error
     */
    String completeStructured(String prompt, String jsonSchema);

    /**
     * Whether the client is configured to make calls (e.g. an API key is present). When false,
     * callers should fall back to deterministic behavior instead of calling.
     */
    boolean isAvailable();
}
