package com.springdebugger.tap;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.springdebugger.classifier.RuleBasedClassifier;
import com.springdebugger.engine.DiagnosisPipeline;
import com.springdebugger.enricher.ActuatorEnricher;
import com.springdebugger.enricher.IdeEnrichmentContext;
import com.springdebugger.enricher.PropertyPrecedenceEnricher;
import com.springdebugger.enricher.PsiEnricher;
import com.springdebugger.extractor.LogExtractor;
import com.springdebugger.llm.LlmFallback;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.model.RawSignal;
import com.springdebugger.rule.RuleCatalog;
import com.springdebugger.ui.DiagnosisCardPanel;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Attaches to a run configuration's process and buffers stdout/stderr, then classifies the
 * output when a Spring Boot error appears.
 *
 * <p>Analysis is debounced: when an error signature is seen, analysis is scheduled a short
 * delay later and rescheduled on every further chunk. This means it runs once the stack has
 * finished streaming (Spring emits the banner and the Caused-by chain in separate chunks), so
 * it works for a runtime exception in a still-running app, not only at process death.
 * Termination is a final backstop. At most one card is shown per run.
 */
public final class RunConsoleTap implements ProcessListener {

    private static final int BUFFER_MAX_CHARS = 200_000;
    private static final String STARTUP_FAILURE_MARKER = "APPLICATION FAILED TO START";
    private static final String RUNTIME_EXCEPTION_MARKER = "Exception in thread";
    /** How long the output must settle after an error signature before we analyse. */
    private static final long DEBOUNCE_MS = 1200;
    /** Captures the bound HTTP port from the Tomcat/Netty/Jetty startup line. */
    private static final Pattern PORT_LINE = Pattern.compile(
            "(?:Tomcat|Netty|Jetty|Undertow)[^\\n]*?started on port[\\s(]*s?[)\\s:]*?(\\d{2,5})");

    private final Project project;
    private final LogExtractor extractor;
    private final DiagnosisPipeline pipeline;
    private final StringBuilder buffer = new StringBuilder();
    private volatile boolean cardShown = false;
    private volatile Phase currentPhase = Phase.STARTUP;
    private volatile int appPort = -1;
    private volatile ScheduledFuture<?> pending;

    public RunConsoleTap(Project project, RuleCatalog catalog) {
        this.project = project;
        this.extractor = new LogExtractor();
        this.pipeline = new DiagnosisPipeline(
                new RuleBasedClassifier(catalog),
                List.of(new PsiEnricher(), new ActuatorEnricher(), new PropertyPrecedenceEnricher()),
                LlmFallback.fromSettings());
    }

    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        String text = event.getText();
        synchronized (buffer) {
            if (buffer.length() < BUFFER_MAX_CHARS) {
                buffer.append(text);
            }
        }

        if (text.contains(STARTUP_FAILURE_MARKER)) {
            currentPhase = Phase.STARTUP;
        } else if (text.contains(RUNTIME_EXCEPTION_MARKER)) {
            currentPhase = Phase.RUNTIME;
        }

        if (appPort < 0 && text.contains("started on port")) {
            Matcher m = PORT_LINE.matcher(text);
            if (m.find()) {
                try {
                    appPort = Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {
                    // leave appPort unset; Actuator enrichment simply will not fire
                }
            }
        }

        // Debounce: schedule (or reschedule) analysis once an error signature appears, so it
        // fires after the stack has finished streaming rather than on a partial buffer.
        if (!cardShown && containsErrorSignature(text)) {
            scheduleAnalysis();
        }
    }

    @Override
    public void processTerminated(@NotNull ProcessEvent event) {
        ScheduledFuture<?> p = pending;
        if (p != null) p.cancel(false);
        analyseBuffer();
    }

    private void scheduleAnalysis() {
        ScheduledFuture<?> previous = pending;
        if (previous != null) previous.cancel(false);
        pending = AppExecutorUtil.getAppScheduledExecutorService()
                .schedule(this::analyseBuffer, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void analyseBuffer() {
        if (cardShown) return;
        String snapshot;
        synchronized (buffer) {
            snapshot = buffer.toString();
        }
        RawSignal signal = extractor.extract(snapshot, currentPhase);
        Optional<DiagnosisCard> card = pipeline.run(signal, new IdeEnrichmentContext(project, appPort));
        card.ifPresent(c -> {
            cardShown = true;
            DiagnosisCardPanel.show(project, c);
        });
    }

    /** True if a chunk looks like the start/body of an error worth analysing. Pure: tested. */
    static boolean containsErrorSignature(String text) {
        if (text == null) return false;
        return text.contains(STARTUP_FAILURE_MARKER)
                || text.contains(RUNTIME_EXCEPTION_MARKER)
                || text.contains("Caused by:")
                || (text.contains("ERROR") && text.contains("Exception"));
    }

    @Override public void startNotified(@NotNull ProcessEvent event) {}
    @Override public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {}
}
