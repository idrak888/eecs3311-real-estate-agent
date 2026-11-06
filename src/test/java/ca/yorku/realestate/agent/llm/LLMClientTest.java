package ca.yorku.realestate.agent.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LLMClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Captures the request and replies with a canned response. */
    static class FakeTransport implements AnthropicLLMClient.Transport {
        final List<Map<String, String>> headers = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        int status = 200;
        java.util.LinkedList<Integer> statuses = new java.util.LinkedList<>();
        int calls;
        String reply = "{\"content\":[{\"type\":\"text\",\"text\":\"Hello\"}],"
                + "\"usage\":{\"input_tokens\":12,\"output_tokens\":3}}";
        IOException failWith;

        @Override
        public AnthropicLLMClient.HttpResult post(String url, Map<String, String> h, String body)
                throws IOException {
            calls++;
            if (failWith != null) {
                throw failWith;
            }
            headers.add(h);
            bodies.add(body);
            int s = statuses.isEmpty() ? status : statuses.removeFirst();
            return new AnthropicLLMClient.HttpResult(s, s == 200 ? reply
                    : "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}");
        }
    }

    // ---- MockLLMClient ----

    @Test
    void mockMatchesLongestKeyAndRecordsPrompts() {
        MockLLMClient mock = new MockLLMClient();
        mock.setResponse("house", "generic");
        mock.setResponse("house under 850k", "specific");
        assertEquals("specific", mock.complete("3 bed house under 850k please"));
        assertEquals("generic", mock.complete("a house"));
        assertEquals(2, mock.getCallCount());
        assertTrue(mock.getPrompts().get(0).contains("850k"));
    }

    @Test
    void mockDefaultAndUnmatched() {
        MockLLMClient mock = new MockLLMClient();
        assertThrows(LLMException.class, () -> mock.complete("anything"));
        mock.setDefaultResponse("fallback");
        assertEquals("fallback", mock.complete("anything"));
    }

    @Test
    void mockStructuredIncludesSchemaInRecordedPrompt() {
        MockLLMClient mock = new MockLLMClient();
        mock.setDefaultResponse("{}");
        mock.completeStructured("find me a condo", "{\"type\":\"object\"}");
        assertTrue(mock.getPrompts().get(0).contains("\"type\":\"object\""));
    }

    @Test
    void mockUnavailableThrows() {
        MockLLMClient mock = new MockLLMClient();
        mock.setDefaultResponse("x");
        mock.setAvailable(false);
        assertFalse(mock.isAvailable());
        assertThrows(LLMException.class, () -> mock.complete("hi"));
    }

    // ---- AnthropicLLMClient ----

    @Test
    void sendsKeyVersionAndModelAndParsesText() throws IOException {
        FakeTransport t = new FakeTransport();
        AnthropicLLMClient client = new AnthropicLLMClient("sk-test", "claude-test", 256, t);
        assertEquals("Hello", client.complete("Hi there"));

        Map<String, String> h = t.headers.get(0);
        assertEquals("sk-test", h.get("x-api-key"));
        assertEquals(AnthropicLLMClient.API_VERSION, h.get("anthropic-version"));
        JsonNode body = JSON.readTree(t.bodies.get(0));
        assertEquals("claude-test", body.get("model").asText());
        assertEquals(256, body.get("max_tokens").asInt());
        assertEquals("user", body.get("messages").get(0).get("role").asText());
        assertEquals("Hi there", body.get("messages").get(0).get("content").asText());
        assertFalse(body.has("system"));
    }

    @Test
    void structuredPutsSchemaInSystemPrompt() throws IOException {
        FakeTransport t = new FakeTransport();
        new AnthropicLLMClient("k", null, 100, t).completeStructured("q", "{\"S\":1}");
        JsonNode body = JSON.readTree(t.bodies.get(0));
        assertTrue(body.get("system").asText().contains("{\"S\":1}"));
        assertTrue(body.get("system").asText().contains("Output only the JSON"));
    }

    @Test
    void concatenatesTextBlocksAndCountsTokens() {
        FakeTransport t = new FakeTransport();
        t.reply = "{\"content\":[{\"type\":\"text\",\"text\":\"a\"},{\"type\":\"other\"},"
                + "{\"type\":\"text\",\"text\":\"b\"}],\"usage\":{\"input_tokens\":5,\"output_tokens\":7}}";
        AnthropicLLMClient client = new AnthropicLLMClient("k", "m", 10, t);
        assertEquals("ab", client.complete("x"));
        client.complete("y");
        assertEquals(10, client.getInputTokens());
        assertEquals(14, client.getOutputTokens());
    }

    @Test
    void missingKeyIsUnavailableAndNeverCalls() {
        FakeTransport t = new FakeTransport();
        AnthropicLLMClient client = new AnthropicLLMClient(null, "m", 10, t);
        assertFalse(client.isAvailable());
        LLMException e = assertThrows(LLMException.class, () -> client.complete("x"));
        assertTrue(e.getMessage().contains(".env"));
        assertTrue(t.bodies.isEmpty());
    }

    @Test
    void httpErrorIsReportedWithoutTheKey() {
        FakeTransport t = new FakeTransport();
        AnthropicLLMClient.Transport authFails = (url, h, body) -> new AnthropicLLMClient.HttpResult(401,
                "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}");
        LLMException e = assertThrows(LLMException.class,
                () -> new AnthropicLLMClient("sk-secret-123", "m", 10, authFails).complete("x"));
        assertTrue(e.getMessage().contains("401"));
        assertTrue(e.getMessage().contains("invalid x-api-key"));
        assertFalse(e.getMessage().contains("sk-secret-123"));
    }

    @Test
    void networkFailureBecomesLLMException() {
        FakeTransport t = new FakeTransport();
        t.failWith = new IOException("timed out");
        LLMException e = assertThrows(LLMException.class,
                () -> new AnthropicLLMClient("k", "m", 10, t, 1, ms -> { }).complete("x"));
        assertTrue(e.getMessage().contains("timed out"));
        assertEquals(2, t.calls, "one retry after a network failure");
    }

    @Test
    void malformedOrEmptyResponseIsAnError() {
        FakeTransport t = new FakeTransport();
        t.reply = "not json";
        assertThrows(LLMException.class, () -> new AnthropicLLMClient("k", "m", 10, t).complete("x"));
        t.reply = "{\"content\":[]}";
        assertThrows(LLMException.class, () -> new AnthropicLLMClient("k", "m", 10, t).complete("x"));
    }

    @Test
    void promptTextIsJsonEscaped() throws IOException {
        FakeTransport t = new FakeTransport();
        new AnthropicLLMClient("k", "m", 10, t).complete("quote \" and\nnewline");
        assertEquals("quote \" and\nnewline",
                JSON.readTree(t.bodies.get(0)).get("messages").get(0).get("content").asText());
    }

    // ---- retries ----

    @Test
    void temporaryFailureIsRetriedOnceThenSucceeds() {
        FakeTransport t = new FakeTransport();
        t.statuses.add(529);
        List<Long> pauses = new ArrayList<>();
        AnthropicLLMClient client = new AnthropicLLMClient("k", "m", 10, t, 1, pauses::add);
        assertEquals("Hello", client.complete("x"));
        assertEquals(2, t.calls);
        assertEquals(List.of(AnthropicLLMClient.RETRY_DELAY_MS), pauses);
    }

    @Test
    void persistentOverloadFailsAfterOneRetryWithReason() {
        FakeTransport t = new FakeTransport();
        t.status = 429;
        LLMException e = assertThrows(LLMException.class,
                () -> new AnthropicLLMClient("k", "m", 10, t, 1, ms -> { }).complete("x"));
        assertEquals(2, t.calls);
        assertTrue(e.getMessage().contains("HTTP 429"));
        assertTrue(e.getMessage().contains("after retrying"));
    }

    @Test
    void permanentErrorsAreNotRetried() {
        FakeTransport t = new FakeTransport();
        t.status = 400;
        assertThrows(LLMException.class, () -> new AnthropicLLMClient("k", "m", 10, t, 1, ms -> { }).complete("x"));
        assertEquals(1, t.calls);
    }

    @Test
    void temporaryStatusClassification() {
        assertTrue(AnthropicLLMClient.isTemporary(429));
        assertTrue(AnthropicLLMClient.isTemporary(500));
        assertTrue(AnthropicLLMClient.isTemporary(529));
        assertFalse(AnthropicLLMClient.isTemporary(400));
        assertFalse(AnthropicLLMClient.isTemporary(401));
        assertFalse(AnthropicLLMClient.isTemporary(404));
    }
}
