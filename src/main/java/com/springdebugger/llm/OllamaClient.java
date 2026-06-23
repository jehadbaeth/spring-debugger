package com.springdebugger.llm;

import java.util.Optional;

/**
 * Abstraction over a local Ollama instance. Kept as an interface so the
 * {@link LlmDiagnosisEngine} can be unit-tested against stubbed responses without any
 * network access.
 */
public interface OllamaClient {

    /**
     * Sends a prompt to the model and returns the model's text response, or empty on any
     * failure (unreachable, timeout, non-2xx, unparseable envelope).
     */
    Optional<String> generate(String prompt);
}
