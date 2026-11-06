package ca.yorku.realestate.agent.llm;

import ca.yorku.realestate.config.EnvConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adapter from {@link LLMClient} to the Anthropic Messages API, using the JDK HTTP client.
 *
 * <p>The API key comes from local configuration ({@code .env}) and is sent only in the
 * {@code x-api-key} header; it never appears in exceptions or logs. Token usage reported by the
 * API is accumulated so the AI usage log can be filled from real numbers.
 *
 * <p>Temporary failures (network errors, timeouts, HTTP 429 rate limits and 5xx/529 overloads)
 * are retried once after a short pause. Permanent ones, such as a bad key (401) or bad request
 * (400), fail immediately.
 */
public class AnthropicLLMClient implements LLMClient {

    public static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    public static final String API_VERSION = "2023-06-01";
    public static final int DEFAULT_MAX_TOKENS = 1024;
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    /** Extra attempts after a temporary failure. */
    public static final int DEFAULT_RETRIES = 1;
    /** Pause before a retry, in milliseconds. */
    public static final long RETRY_DELAY_MS = 750;

    /** Sends one HTTP request; replaced in tests so no network is needed. */
    @FunctionalInterface
    public interface Transport {
        HttpResult post(String url, Map<String, String> headers, String body) throws IOException;
    }

    /** Status code and body of an HTTP response. */
    public record HttpResult(int status, String body) {
    }

    /** Pauses between attempts; replaced in tests so they run instantly. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final Transport transport;
    private final int retries;
    private final Sleeper sleeper;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();

    public AnthropicLLMClient(String apiKey, String model, int maxTokens, Transport transport) {
        this(apiKey, model, maxTokens, transport, DEFAULT_RETRIES, Thread::sleep);
    }

    AnthropicLLMClient(String apiKey, String model, int maxTokens, Transport transport, int retries, Sleeper sleeper) {
        this.apiKey = apiKey;
        this.model = model == null || model.isBlank() ? EnvConfig.DEFAULT_MODEL : model;
        this.maxTokens = maxTokens;
        this.transport = transport;
        this.retries = Math.max(0, retries);
        this.sleeper = sleeper;
    }

    /** HTTP statuses worth one more try: rate limited, server error, overloaded. */
    static boolean isTemporary(int status) {
        return status == 429 || status == 529 || (status >= 500 && status <= 599);
    }

    /** Client configured from {@code .env} / environment, using the JDK HTTP client. */
    public static AnthropicLLMClient fromConfig(EnvConfig config) {
        return new AnthropicLLMClient(config.apiKey().orElse(null), config.model(),
                DEFAULT_MAX_TOKENS, jdkTransport(DEFAULT_TIMEOUT));
    }

    static Transport jdkTransport(Duration timeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).build();
        return (url, headers, body) -> {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(req::header);
            try {
                HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
                return new HttpResult(resp.statusCode(), resp.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        };
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String complete(String prompt) {
        return send(null, prompt);
    }

    @Override
    public String completeStructured(String prompt, String jsonSchema) {
        String system = "Respond with exactly one JSON object that conforms to this JSON Schema. "
                + "Output only the JSON: no prose, no explanation, no markdown code fences.\n"
                + "Schema:\n" + jsonSchema;
        return send(system, prompt);
    }

    private String send(String system, String prompt) {
        if (!isAvailable()) {
            throw new LLMException("No LLM API key is configured. Copy .env.example to .env and add your key.");
        }
        String body = requestBody(system, prompt);
        Map<String, String> headers = Map.of(
                "x-api-key", apiKey,
                "anthropic-version", API_VERSION,
                "content-type", "application/json");
        for (int attempt = 0; ; attempt++) {
            boolean lastAttempt = attempt >= retries;
            HttpResult result;
            try {
                result = transport.post(ENDPOINT, headers, body);
            } catch (IOException e) {
                if (lastAttempt) {
                    throw new LLMException("Could not reach the language model: " + e.getMessage(), e);
                }
                pause(attempt);
                continue;
            }
            if (result.status() == 200) {
                return parseText(result.body());
            }
            if (lastAttempt || !isTemporary(result.status())) {
                throw new LLMException("Language model returned HTTP " + result.status()
                        + errorDetail(result.body()) + (attempt > 0 ? " (after retrying)" : ""));
            }
            pause(attempt);
        }
    }

    private void pause(int attempt) {
        try {
            sleeper.sleep(RETRY_DELAY_MS * (attempt + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMException("Interrupted while waiting to retry", e);
        }
    }

    String requestBody(String system, String prompt) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", maxTokens);
        if (system != null) {
            root.put("system", system);
        }
        ArrayNode messages = root.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", prompt);
        try {
            return mapper.writeValueAsString(root);
        } catch (IOException e) {
            throw new LLMException("Could not build request", e);
        }
    }

    private String parseText(String body) {
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (IOException e) {
            throw new LLMException("Language model returned malformed JSON", e);
        }
        JsonNode usage = root.path("usage");
        inputTokens.addAndGet(usage.path("input_tokens").asLong(0));
        outputTokens.addAndGet(usage.path("output_tokens").asLong(0));

        StringBuilder text = new StringBuilder();
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        if (text.length() == 0) {
            throw new LLMException("Language model returned no text");
        }
        return text.toString();
    }

    private String errorDetail(String body) {
        try {
            String msg = mapper.readTree(body).path("error").path("message").asText("");
            return msg.isBlank() ? "" : ": " + msg;
        } catch (IOException e) {
            return "";
        }
    }

    public String getModel() {
        return model;
    }

    public long getInputTokens() {
        return inputTokens.get();
    }

    public long getOutputTokens() {
        return outputTokens.get();
    }
}
