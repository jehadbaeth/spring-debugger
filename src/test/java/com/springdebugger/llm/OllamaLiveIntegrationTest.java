package com.springdebugger.llm;

import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.model.RawSignal;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A genuine live round-trip against a local Ollama instance with a real model installed.
 * Skipped automatically (JUnit assumption) when Ollama is not reachable, so it never breaks
 * a CI box without Ollama. When it does run, it proves the real HTTP call reaches a real
 * model and the response flows through the parse path — the part a stub server cannot prove.
 */
class OllamaLiveIntegrationTest {

    private static final String BASE_URL = "http://localhost:11434";
    private static final String MODEL = "qwen2:0.5b";

    private static boolean ollamaReachable() {
        try {
            HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest r = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/tags")).timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<String> resp = c.send(r, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 && resp.body().contains(MODEL);
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void liveModelRespondsAndRoundTripCompletes() {
        assumeTrue(ollamaReachable(), "Ollama with " + MODEL + " not reachable; skipping live test");

        OllamaClient client = new OllamaHttpClient(BASE_URL, MODEL, Duration.ofSeconds(60));

        // 1) The client actually reaches a real model and gets a non-empty response.
        Optional<String> raw = client.generate(
                "Respond ONLY with a JSON object: {\"diagnosis\":\"the app failed to start\","
                + "\"fix\":\"add the missing bean\"}");
        assertThat(raw).as("a real model behind real HTTP should answer").isPresent();

        // 2) The full engine path completes without throwing. With format=json the model
        //    returns valid JSON; if it omits a field the safety contract yields empty. Either
        //    outcome is acceptable — what matters is the live path runs end to end and any
        //    card produced is well formed.
        RawSignal signal = new RawSignal(Phase.STARTUP,
                "org.springframework.beans.factory.UnsatisfiedDependencyException",
                "Error creating bean 'orderService'", null, null, null, -1,
                List.of("Error creating bean 'orderService'"), "excerpt");
        Optional<DiagnosisCard> card = new LlmDiagnosisEngine(client).diagnose(signal);

        card.ifPresent(c -> {
            assertThat(c.getRuleId()).isEqualTo("llm");
            assertThat(c.getDiagnosisSentence()).isNotBlank();
            assertThat(c.getFixSentence()).isNotBlank();
        });
    }
}
