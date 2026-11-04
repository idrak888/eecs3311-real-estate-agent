package ca.yorku.realestate.agent;

import java.time.Instant;

/** One exchange in the conversation: what the user said and what the agent replied. */
public final class ConversationTurn {

    private final String userMessage;
    private final String agentResponse;
    private final Instant timestamp;

    public ConversationTurn(String userMessage, String agentResponse, Instant timestamp) {
        if (userMessage == null) {
            throw new IllegalArgumentException("user message is required");
        }
        this.userMessage = userMessage;
        this.agentResponse = agentResponse == null ? "" : agentResponse;
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public ConversationTurn(String userMessage, String agentResponse) {
        this(userMessage, agentResponse, Instant.now());
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getAgentResponse() {
        return agentResponse;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
