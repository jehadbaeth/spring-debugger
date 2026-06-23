package com.springdebugger.tap;

import com.intellij.openapi.project.Project;
import com.springdebugger.classifier.RuleBasedClassifier;
import com.springdebugger.engine.DiagnosisPipeline;
import com.springdebugger.enricher.ActuatorEnricher;
import com.springdebugger.enricher.EnrichmentContext;
import com.springdebugger.enricher.IdeEnrichmentContext;
import com.springdebugger.enricher.PropertyPrecedenceEnricher;
import com.springdebugger.enricher.PsiEnricher;
import com.springdebugger.extractor.LogExtractor;
import com.springdebugger.extractor.StackTraceSegmenter;
import com.springdebugger.llm.LlmFallback;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.rule.RuleCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared diagnosis for any chunk of console-style output (a delegated Gradle/Maven run, or a
 * terminal session). It runs the full pipeline and tries the phases in the order they occur in
 * practice: STARTUP (DI, datasource, Kafka, ...), then COMPILE (build rules via the
 * Spring-marker gate), then RUNTIME. The phase filter ensures rules that do not apply at a
 * given phase fall through to the next pass.
 *
 * <p>Project-tolerant: with a null project (unit tests) enrichment simply does not engage.
 */
public final class ConsoleDiagnoser {

    private final LogExtractor extractor = new LogExtractor();
    private final RuleCatalog catalog;

    public ConsoleDiagnoser(RuleCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Diagnoses every distinct error in a noisy buffer: splits it into per-error blocks,
     * diagnoses each, and de-duplicates by rule + diagnosis so an endpoint hit ten times does
     * not produce ten cards. Order is preserved (first occurrence wins). This is what an
     * integration run against a long-lived app needs, where many server-side errors interleave.
     */
    public List<DiagnosisCard> diagnoseAll(String output, Project project) {
        return diagnoseAllWith(output, project != null ? new IdeEnrichmentContext(project) : null);
    }

    /** As above, but with a caller-supplied enrichment context (e.g. one that knows the app port). */
    public List<DiagnosisCard> diagnoseAllWith(String output, EnrichmentContext context) {
        DiagnosisPipeline pipeline = buildPipeline();
        Map<String, DiagnosisCard> byKey = new LinkedHashMap<>();
        for (String block : StackTraceSegmenter.segment(output)) {
            diagnoseBlock(block, context, pipeline).ifPresent(card -> byKey.putIfAbsent(key(card), card));
        }
        return new ArrayList<>(byKey.values());
    }

    /** Stable identity of a diagnosis for de-duplication and history grouping. */
    public static String key(DiagnosisCard card) {
        return card.groupingKey();
    }

    public Optional<DiagnosisCard> diagnose(String output, Project project) {
        EnrichmentContext context = project != null ? new IdeEnrichmentContext(project) : null;
        return diagnoseBlock(output, context, buildPipeline());
    }

    private DiagnosisPipeline buildPipeline() {
        return new DiagnosisPipeline(
                new RuleBasedClassifier(catalog),
                List.of(new PsiEnricher(), new ActuatorEnricher(), new PropertyPrecedenceEnricher()),
                LlmFallback.fromSettings());
    }

    private Optional<DiagnosisCard> diagnoseBlock(String output, EnrichmentContext context,
                                                  DiagnosisPipeline pipeline) {
        if (output == null || output.isBlank()) return Optional.empty();

        Optional<DiagnosisCard> startup = pipeline.run(extractor.extract(output, Phase.STARTUP), context);
        if (startup.isPresent()) return startup;

        Optional<DiagnosisCard> compile = new BuildOutputAnalyzer(catalog).analyze(output);
        if (compile.isPresent()) return compile;

        return pipeline.run(extractor.extract(output, Phase.RUNTIME), context);
    }
}
