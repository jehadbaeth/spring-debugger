package com.springdebugger.llm;

import com.springdebugger.engine.DiagnosisEngine;
import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.RawSignal;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.util.Optional;

/**
 * LLM fallback engine (M13). Used only when the rule engine returns no match (the pipeline's
 * last resort). Builds a tightly scoped prompt from the extracted signal, asks a local Ollama
 * model for a one-sentence diagnosis and one-sentence fix as JSON, and parses the reply into a
 * {@link DiagnosisCard} labelled {@code ruleId="llm"} at MEDIUM confidence.
 *
 * <p>Safety contract (PLAN 8.3): if the response cannot be parsed into both fields, nothing is
 * shown. A malformed model reply must never surface as a confident diagnosis. LLM output is
 * always MEDIUM, never HIGH, so it ranks below any rule match and is clearly distinguishable.
 */
public final class LlmDiagnosisEngine implements DiagnosisEngine {

    private final OllamaClient client;

    public LlmDiagnosisEngine(OllamaClient client) {
        this.client = client;
    }

    @Override
    public Optional<DiagnosisCard> diagnose(RawSignal signal) {
        if (signal == null) return Optional.empty();
        String prompt = buildPrompt(signal);
        Optional<String> reply = client.generate(prompt);
        return reply.flatMap(r -> parseCard(r, signal));
    }

    static String buildPrompt(RawSignal signal) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a Spring Boot error triage assistant. ")
                .append("Given the extracted signal from a failing Spring Boot application, ")
                .append("respond ONLY with a JSON object of exactly two string fields: ")
                .append("\"diagnosis\" (one sentence naming the precise problem) and ")
                .append("\"fix\" (one sentence giving the single corrective action). ")
                .append("Do not include any other text.\n\n");
        sb.append("Phase: ").append(signal.getPhase()).append('\n');
        if (signal.getDeepestCausedByClass() != null) {
            sb.append("Deepest exception: ").append(signal.getDeepestCausedByClass()).append('\n');
        }
        if (signal.getDeepestCausedByMessage() != null) {
            sb.append("Exception message: ").append(signal.getDeepestCausedByMessage()).append('\n');
        }
        if (signal.getBannerDescription() != null) {
            sb.append("Failure analyzer description: ").append(signal.getBannerDescription()).append('\n');
        }
        if (signal.getBannerAction() != null) {
            sb.append("Failure analyzer action: ").append(signal.getBannerAction()).append('\n');
        }
        if (signal.getFailingBeanName() != null) {
            sb.append("Failing bean: ").append(signal.getFailingBeanName()).append('\n');
        }
        sb.append("\nLog excerpt:\n").append(truncate(signal.getRawExcerpt(), 4000));
        return sb.toString();
    }

    /** Parses the model's JSON reply. Returns empty unless BOTH diagnosis and fix are present. */
    static Optional<DiagnosisCard> parseCard(String reply, RawSignal signal) {
        if (reply == null || reply.isBlank()) return Optional.empty();
        try {
            Object parsed = new Yaml().load(reply);
            if (!(parsed instanceof Map<?, ?> map)) return Optional.empty();
            Object diagnosis = map.get("diagnosis");
            Object fix = map.get("fix");
            if (!(diagnosis instanceof String d) || !(fix instanceof String f)) return Optional.empty();
            if (d.isBlank() || f.isBlank()) return Optional.empty();
            return Optional.of(new DiagnosisCard(
                    "llm",
                    signal.getPhase(),
                    d.trim(),
                    f.trim(),
                    Confidence.MEDIUM,
                    signal.getRawExcerpt()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "\n...[truncated]";
    }
}
