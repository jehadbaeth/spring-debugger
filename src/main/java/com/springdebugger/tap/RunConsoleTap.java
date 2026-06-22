package com.springdebugger.tap;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.springdebugger.classifier.RuleBasedClassifier;
import com.springdebugger.extractor.LogExtractor;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.model.RawSignal;
import com.springdebugger.rule.RuleCatalog;
import com.springdebugger.ui.DiagnosisCardPanel;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Attaches to a run configuration's process and buffers stdout/stderr.
 * Triggers extraction and classification when Spring Boot error patterns appear.
 */
public final class RunConsoleTap implements ProcessListener {

    private static final int BUFFER_MAX_CHARS = 200_000;
    private static final String STARTUP_FAILURE_MARKER = "APPLICATION FAILED TO START";
    private static final String RUNTIME_EXCEPTION_MARKER = "Exception in thread";

    private final Project project;
    private final LogExtractor extractor;
    private final RuleBasedClassifier classifier;
    private final StringBuilder buffer = new StringBuilder();
    private boolean startupFailureDetected = false;
    private Phase currentPhase = Phase.STARTUP;

    public RunConsoleTap(Project project, RuleCatalog catalog) {
        this.project = project;
        this.extractor = new LogExtractor();
        this.classifier = new RuleBasedClassifier(catalog);
    }

    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        String text = event.getText();
        if (buffer.length() < BUFFER_MAX_CHARS) {
            buffer.append(text);
        }

        if (!startupFailureDetected && text.contains(STARTUP_FAILURE_MARKER)) {
            startupFailureDetected = true;
            currentPhase = Phase.STARTUP;
        }

        if (text.contains(RUNTIME_EXCEPTION_MARKER)) {
            currentPhase = Phase.RUNTIME;
        }
    }

    @Override
    public void processTerminated(@NotNull ProcessEvent event) {
        if (event.getExitCode() != 0 || startupFailureDetected) {
            analyseBuffer();
        }
    }

    private void analyseBuffer() {
        RawSignal signal = extractor.extract(buffer.toString(), currentPhase);
        Optional<DiagnosisCard> card = classifier.classify(signal);
        card.ifPresent(c -> DiagnosisCardPanel.show(project, c));
    }

    @Override public void startNotified(@NotNull ProcessEvent event) {}
    @Override public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {}
}
