package com.springdebugger.llm;

import com.intellij.openapi.application.ApplicationManager;
import com.springdebugger.engine.DiagnosisEngine;
import com.springdebugger.settings.SpringDebuggerSettings;

import java.time.Duration;

/**
 * Builds the LLM fallback engine from the current settings, or returns null when the user has
 * not enabled it. Returning null keeps the pipeline on the pure offline path, so a default
 * install (LLM off) behaves exactly as before.
 */
public final class LlmFallback {

    /** Generous bound: a cold Ollama model load can take tens of seconds; this still caps it. */
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private LlmFallback() {}

    public static DiagnosisEngine fromSettings() {
        // No application means we are outside a running IDE (e.g. a unit test); there is no
        // LLM fallback to build, so stay on the offline path.
        if (ApplicationManager.getApplication() == null) return null;
        SpringDebuggerSettings settings = SpringDebuggerSettings.getInstance();
        if (!settings.isLlmEnabled()) return null;
        OllamaClient client = new OllamaHttpClient(
                settings.getOllamaBaseUrl(), settings.getOllamaModel(), TIMEOUT);
        return new LlmDiagnosisEngine(client);
    }
}
