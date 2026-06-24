package com.springdebugger.tap;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.springdebugger.enricher.IdeEnrichmentContext;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.rule.RuleCatalog;
import com.springdebugger.ui.DiagnosisCardPanel;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Attaches to a run configuration's process, buffers stdout/stderr, and classifies the output
 * when a Spring Boot error appears.
 *
 * <p>Analysis is debounced: when an error signature is seen it is scheduled a short delay later
 * and rescheduled on every further chunk, so it runs once the stack has finished streaming and
 * works for a runtime exception in a still-running app, not only at process death. It surfaces
 * every distinct error (an integration run can throw several), de-duplicated per run, and only
 * balloons the first of a burst so negative tests do not flood the UI.
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

    private static final com.intellij.openapi.diagnostic.Logger LOG =
            com.intellij.openapi.diagnostic.Logger.getInstance(RunConsoleTap.class);

    private final Project project;
    private final ConsoleDiagnoser diagnoser;
    private final StringBuilder buffer = new StringBuilder();
    private final Set<String> shownKeys = new HashSet<>();
    private volatile int appPort = -1;
    private volatile boolean sawOutput = false;
    private volatile ScheduledFuture<?> pending;

    public RunConsoleTap(Project project, RuleCatalog catalog) {
        this.project = project;
        this.diagnoser = new ConsoleDiagnoser(catalog);
    }

    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        String text = event.getText();
        if (!sawOutput) {
            sawOutput = true;
            LOG.info("RunConsoleTap receiving process output (first chunk " + text.length() + " chars)");
        }
        synchronized (buffer) {
            // Keep the tail rather than dropping everything past the cap: in a long bootRun the
            // error (Kafka down, a late exception) arrives after a large successful-startup log, so
            // a cap-and-stop buffer would discard exactly what we need.
            buffer.append(text);
            if (buffer.length() > BUFFER_MAX_CHARS) {
                buffer.delete(0, buffer.length() - BUFFER_MAX_CHARS);
            }
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

        // Debounce: (re)schedule analysis whenever an error signature appears, so it fires
        // after the stack has finished streaming rather than on a partial buffer.
        if (containsErrorSignature(text)) {
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
        String snapshot;
        synchronized (buffer) {
            snapshot = buffer.toString();
        }
        List<DiagnosisCard> cards = diagnoser.diagnoseAllWith(snapshot, new IdeEnrichmentContext(project, appPort));
        LOG.info("RunConsoleTap analysed " + snapshot.length() + " chars, produced " + cards.size() + " card(s)");
        boolean firstOfBurst = true;
        for (DiagnosisCard card : cards) {
            if (!shownKeys.add(card.groupingKey())) continue; // already surfaced this run
            DiagnosisCardPanel.show(project, card, !firstOfBurst);
            firstOfBurst = false;
        }
    }

    /** True if a chunk looks like the start/body of an error worth analysing. Pure: tested. */
    public static boolean containsErrorSignature(String text) {
        if (text == null) return false;
        return text.contains(STARTUP_FAILURE_MARKER)
                || text.contains(RUNTIME_EXCEPTION_MARKER)
                || text.contains("Caused by:")
                || (text.contains("ERROR") && text.contains("Exception"))
                // Connection failures (e.g. Kafka, datasource) are logged at WARN with no
                // exception, but are exactly what we diagnose, so recognise their markers too.
                || text.contains("could not be established")
                || text.contains("Failed to update metadata")
                || text.contains("No endpoint ")
                || text.contains("Resolved [");
    }

    @Override public void startNotified(@NotNull ProcessEvent event) {}
    @Override public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {}
}
