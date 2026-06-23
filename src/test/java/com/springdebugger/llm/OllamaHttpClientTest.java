package com.springdebugger.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the pure protocol helpers of the Ollama client (no network). */
class OllamaHttpClientTest {

    @Test
    void requestBodyIsValidJsonWithEscaping() {
        String body = OllamaHttpClient.buildRequestBody("llama3.2", "line one\n\"quoted\"");
        assertThat(body).contains("\"model\":\"llama3.2\"")
                .contains("\"stream\":false")
                .contains("\"format\":\"json\"")
                .contains("\\n")
                .contains("\\\"quoted\\\"");
    }

    @Test
    void extractsResponseField() {
        String envelope = "{\"model\":\"llama3.2\",\"response\":\"{\\\"diagnosis\\\":\\\"x\\\"}\",\"done\":true}";
        assertThat(OllamaHttpClient.extractResponseField(envelope))
                .contains("{\"diagnosis\":\"x\"}");
    }

    @Test
    void missingResponseFieldIsEmpty() {
        assertThat(OllamaHttpClient.extractResponseField("{\"done\":true}")).isEmpty();
    }

    @Test
    void garbageEnvelopeIsEmpty() {
        assertThat(OllamaHttpClient.extractResponseField("not json")).isEmpty();
        assertThat(OllamaHttpClient.extractResponseField("")).isEmpty();
        assertThat(OllamaHttpClient.extractResponseField(null)).isEmpty();
    }
}
