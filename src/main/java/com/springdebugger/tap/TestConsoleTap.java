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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Listens to the test runner event bus. When the run finishes with a failure, it walks the
 * whole test tree and collects every failed node's stacktrace, not just the root's error
 * message. The root proxy usually carries only a generic "Failed to load ApplicationContext"
 * string; the real Caused-by chain (the missing bean, the bad config) lives on the failed
 * leaf's stacktrace. Reading only the root is why context-load failures used to collapse to
 * the 1.10 umbrella rule instead of the specific cause.
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
        if (testsRoot == null || !testsRoot.isDefect()) return;

        String output = collectFailureText(testsRoot);
        if (output.isBlank() || !isSpringContextFailure(output)) return;

        RawSignal signal = extractor.extract(output, Phase.TEST);
        Optional<DiagnosisCard> card = pipeline.run(signal, new IdeEnrichmentContext(project));
        card.ifPresent(c -> DiagnosisCardPanel.show(project, c));
    }

    /** Gathers stacktrace and error-message fragments from every defective node in the tree. */
    private String collectFailureText(SMTestProxy.SMRootTestProxy testsRoot) {
        List<String> fragments = new ArrayList<>();
        fragments.add(testsRoot.getErrorMessage());
        fragments.add(testsRoot.getStacktrace());
        for (SMTestProxy test : testsRoot.getAllTests()) {
            if (!test.isDefect()) continue;
            fragments.add(test.getStacktrace());
            fragments.add(test.getErrorMessage());
        }
        return buildFailureText(fragments);
    }

    /** Joins non-blank fragments, de-duplicated, preserving order. Pure: unit-tested. */
    static String buildFailureText(List<String> fragments) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String f : fragments) {
            if (f != null && !f.isBlank()) seen.add(f.strip());
        }
        return String.join("\n", seen);
    }

    private boolean isSpringContextFailure(String output) {
        return output.contains("Failed to load ApplicationContext")
                || output.contains("ApplicationContextException")
                || output.contains("BeanCreationException")
                || output.contains("UnsatisfiedDependencyException")
                || output.contains("NoSuchBeanDefinitionException")
                || output.contains("BeanInstantiationException")
                || output.contains("BeanDefinitionStoreException");
    }
}
