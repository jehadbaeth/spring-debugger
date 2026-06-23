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
import com.springdebugger.llm.LlmFallback;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.rule.RuleCatalog;

import java.util.List;
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

    public Optional<DiagnosisCard> diagnose(String output, Project project) {
        if (output == null || output.isBlank()) return Optional.empty();

        EnrichmentContext context = project != null ? new IdeEnrichmentContext(project) : null;
        DiagnosisPipeline pipeline = new DiagnosisPipeline(
                new RuleBasedClassifier(catalog),
                List.of(new PsiEnricher(), new ActuatorEnricher(), new PropertyPrecedenceEnricher()),
                LlmFallback.fromSettings());

        Optional<DiagnosisCard> startup = pipeline.run(extractor.extract(output, Phase.STARTUP), context);
        if (startup.isPresent()) return startup;

        Optional<DiagnosisCard> compile = new BuildOutputAnalyzer(catalog).analyze(output);
        if (compile.isPresent()) return compile;

        return pipeline.run(extractor.extract(output, Phase.RUNTIME), context);
    }
}
