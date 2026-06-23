package com.springdebugger.llm;

import org.yaml.snakeyaml.Yaml;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Talks to a local Ollama instance over its HTTP API ({@code POST /api/generate}).
 *
 * <p>Cloud providers are deliberately not implemented. Sending error-log text off the
 * machine is an exfiltration path (see rule 3.12, secrets leaking into logs); local Ollama
 * keeps everything on the developer's machine, which is the whole reason this path is
 * acceptable. The {@link OllamaClient} interface leaves room for other providers later, but
 * none ship today.
 *
 * <p>The call is hard-bounded by a request timeout so a cold model load cannot stall the
 * run/test callback thread indefinitely.
 */
public final class OllamaHttpClient implements OllamaClient {

    private final String baseUrl;
    private final String model;
    private final Duration timeout;

    public OllamaHttpClient(String baseUrl, String model, Duration timeout) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "http://localhost:11434";
        this.model = model;
        this.timeout = timeout;
    }

    @Override
    public Optional<String> generate(String prompt) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/generate"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(model, prompt)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) return Optional.empty();
            return extractResponseField(response.body());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Builds the Ollama request envelope. format=json asks the model to emit valid JSON. */
    static String buildRequestBody(String model, String prompt) {
        return "{\"model\":" + jsonString(model)
                + ",\"prompt\":" + jsonString(prompt)
                + ",\"stream\":false,\"format\":\"json\"}";
    }

    /** Pulls the "response" field out of the Ollama envelope. Empty when absent or unparseable. */
    static Optional<String> extractResponseField(String body) {
        if (body == null || body.isBlank()) return Optional.empty();
        try {
            Object parsed = new Yaml().load(body);
            if (parsed instanceof Map<?, ?> map) {
                Object response = map.get("response");
                if (response instanceof String s && !s.isBlank()) return Optional.of(s);
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Minimal JSON string escaping for the two values we send. */
    static String jsonString(String value) {
        if (value == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
