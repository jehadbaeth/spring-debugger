package com.springdebugger.engine;

import com.springdebugger.classifier.RuleBasedClassifier;
import com.springdebugger.enricher.Enricher;
import com.springdebugger.enricher.EnrichmentContext;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.RawSignal;
import com.springdebugger.rule.RuleCatalog;

import java.util.List;
import java.util.Optional;

/**
 * The single entry point every tap (run, test, build) calls to turn a RawSignal
 * into a DiagnosisCard. Centralising the control flow here means the enrichment
 * (M8/M9) and LLM fallback (M13) hook one place instead of three taps.
 *
 * Order of operations:
 * <ol>
 *   <li>Try the offline rule engine.</li>
 *   <li>If a rule fired below HIGH confidence, run the enrichers to confirm or
 *       upgrade the structural claim (only when an {@link EnrichmentContext} is given).</li>
 *   <li>If no rule fired at all, fall back to the LLM engine when one is configured.</li>
 * </ol>
 */
public final class DiagnosisPipeline {

    private final DiagnosisEngine ruleEngine;
    private final List<Enricher> enrichers;
    private final DiagnosisEngine llmFallback;

    /** Offline-only pipeline: rule engine, no enrichers, no LLM. */
    public DiagnosisPipeline(RuleCatalog catalog) {
        this(new RuleBasedClassifier(catalog), List.of(), null);
    }

    public DiagnosisPipeline(DiagnosisEngine ruleEngine,
                             List<Enricher> enrichers,
                             DiagnosisEngine llmFallback) {
        this.ruleEngine = ruleEngine;
        this.enrichers = enrichers != null ? List.copyOf(enrichers) : List.of();
        this.llmFallback = llmFallback;
    }

    /** Runs the pipeline without any enrichment context (offline path). */
    public Optional<DiagnosisCard> run(RawSignal signal) {
        return run(signal, null);
    }

    public Optional<DiagnosisCard> run(RawSignal signal, EnrichmentContext context) {
        Optional<DiagnosisCard> ruleResult = ruleEngine.diagnose(signal);

        if (ruleResult.isPresent()) {
            DiagnosisCard card = ruleResult.get();
            // Run enrichers whenever a context is available. They are additive: they sharpen
            // the message with project specifics (which bean, where, which annotation) and
            // return the card unchanged when they cannot help. This applies even to HIGH
            // matches like 2.1, where the value is specificity, not raising confidence.
            if (context != null) {
                for (Enricher enricher : enrichers) {
                    card = enricher.enrich(card, signal, context);
                }
            }
            return Optional.of(card);
        }

        if (llmFallback != null) {
            return llmFallback.diagnose(signal);
        }
        return Optional.empty();
    }
}
