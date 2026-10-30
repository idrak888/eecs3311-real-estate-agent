package ca.yorku.realestate.agent.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic stand-in for a language model, used throughout the unit tests.
 *
 * <p>{@link #setResponse(String, String)} maps a key to a reply: a prompt containing the key
 * gets that reply, and when several keys match, the longest key wins. Every prompt is recorded
 * so tests can assert what was sent.
 */
public class MockLLMClient implements LLMClient {

    private final Map<String, String> responses = new LinkedHashMap<>();
    private final List<String> prompts = new ArrayList<>();
    private String defaultResponse;
    private boolean available = true;

    /** Replies with {@code response} to any prompt containing {@code promptContains}. */
    public void setResponse(String promptContains, String response) {
        responses.put(promptContains, response);
    }

    /** Reply used when no key matches. Without one, an unmatched prompt throws. */
    public void setDefaultResponse(String response) {
        this.defaultResponse = response;
    }

    /** Simulates a missing key or outage: calls throw and {@link #isAvailable()} is false. */
    public void setAvailable(boolean available) {
        this.available = available;
    }

    /** Every prompt received, in order; structured prompts include their schema. */
    public List<String> getPrompts() {
        return List.copyOf(prompts);
    }

    public int getCallCount() {
        return prompts.size();
    }

    @Override
    public String complete(String prompt) {
        return respond(prompt);
    }

    @Override
    public String completeStructured(String prompt, String jsonSchema) {
        return respond(prompt + "\n[schema]\n" + jsonSchema);
    }

    private String respond(String prompt) {
        if (!available) {
            throw new LLMException("Mock LLM is unavailable");
        }
        prompts.add(prompt);
        String best = null;
        for (String key : responses.keySet()) {
            if (prompt.contains(key) && (best == null || key.length() > best.length())) {
                best = key;
            }
        }
        if (best != null) {
            return responses.get(best);
        }
        if (defaultResponse != null) {
            return defaultResponse;
        }
        throw new LLMException("Mock LLM has no response configured for prompt: "
                + prompt.substring(0, Math.min(80, prompt.length())));
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
