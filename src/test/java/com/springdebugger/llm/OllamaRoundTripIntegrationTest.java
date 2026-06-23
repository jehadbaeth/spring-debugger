package com.springdebugger.llm;

import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.model.RawSignal;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the LLM path over real HTTP against a local stub that imitates Ollama's
 * /api/generate endpoint. This is the closest automated proxy for a live round-trip: it
 * verifies the request is actually sent, the envelope is parsed, and the engine produces a
 * card, all without needing an installed Ollama model. The only thing it does not exercise is
 * a real model's content, which is outside our control.
 */
class OllamaRoundTripIntegrationTest {

    private HttpServer server;
    private String baseUrl;
    private volatile String lastRequestBody;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            // Ollama wraps the model output in a "response" string field.
            String body = "{\"model\":\"llama3.2\",\"created_at\":\"now\","
                    + "\"response\":\"{\\\"diagnosis\\\":\\\"A required bean is missing.\\\","
                    + "\\\"fix\\\":\\\"Declare the bean or add its starter.\\\"}\",\"done\":true}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    private static RawSignal signal() {
        return new RawSignal(Phase.STARTUP, "com.example.MysteryException", "no idea",
                null, null, null, -1, List.of("no idea"), "excerpt");
    }

    @Test
    void fullRoundTripProducesCard() {
        OllamaClient client = new OllamaHttpClient(baseUrl, "llama3.2", Duration.ofSeconds(5));
        LlmDiagnosisEngine engine = new LlmDiagnosisEngine(client);

        Optional<DiagnosisCard> card = engine.diagnose(signal());

        assertThat(card).isPresent();
        assertThat(card.get().getRuleId()).isEqualTo("llm");
        assertThat(card.get().getConfidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(card.get().getDiagnosisSentence()).isEqualTo("A required bean is missing.");
        assertThat(card.get().getFixSentence()).isEqualTo("Declare the bean or add its starter.");
        // The request actually carried our prompt and the JSON format directive.
        assertThat(lastRequestBody).contains("MysteryException").contains("\"format\":\"json\"");
    }

    @Test
    void unreachablePortYieldsNoCard() {
        // Point at a closed port: the engine must fail closed (no card), never throw.
        OllamaClient client = new OllamaHttpClient("http://127.0.0.1:1", "llama3.2", Duration.ofMillis(300));
        LlmDiagnosisEngine engine = new LlmDiagnosisEngine(client);

        assertThat(engine.diagnose(signal())).isEmpty();
    }
}
