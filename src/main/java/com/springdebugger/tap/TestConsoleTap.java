package com.springdebugger.tap;

import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.project.Project;
import com.springdebugger.classifier.RuleBasedClassifier;
import com.springdebugger.engine.DiagnosisPipeline;
import com.springdebugger.enricher.IdeEnrichmentContext;
import com.springdebugger.enricher.PsiEnricher;
import com.springdebugger.extractor.LogExtractor;
import com.springdebugger.llm.LlmFallback;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.model.RawSignal;
import com.springdebugger.rule.RuleCatalog;
import com.springdebugger.ui.DiagnosisCardPanel;

import java.util.List;
import java.util.Optional;

/**
 * Listens to the test runner event bus.
 * When the root test suite finishes and has infrastructure failures
 * (e.g. Failed to load ApplicationContext), it sends the output for classification.
 */
public final class TestConsoleTap extends SMTRunnerEventsAdapter {

    private final Project project;
    private final LogExtractor extractor;
    private final DiagnosisPipeline pipeline;

    public TestConsoleTap(Project project, RuleCatalog catalog) {
        this.project = project;
        this.extractor = new LogExtractor();
        this.pipeline = new DiagnosisPipeline(
                new RuleBasedClassifier(catalog),
                List.of(new PsiEnricher()),
                LlmFallback.fromSettings());
    }

    @Override
    public void onTestingFinished(SMTestProxy.SMRootTestProxy testsRoot) {
        if (!testsRoot.isDefect()) return;

        String output = testsRoot.getErrorMessage() != null ? testsRoot.getErrorMessage() : "";
        if (output.isEmpty()) return;

        if (isSpringContextFailure(output)) {
            RawSignal signal = extractor.extract(output, Phase.TEST);
            Optional<DiagnosisCard> card = pipeline.run(signal, new IdeEnrichmentContext(project));
            card.ifPresent(c -> DiagnosisCardPanel.show(project, c));
        }
    }

    private boolean isSpringContextFailure(String output) {
        return output.contains("Failed to load ApplicationContext")
                || output.contains("ApplicationContextException")
                || output.contains("BeanCreationException")
                || output.contains("UnsatisfiedDependencyException");
    }
}
