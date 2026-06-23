package com.springdebugger.terminal;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.springdebugger.SpringDebuggerService;
import com.springdebugger.tap.ConsoleDiagnoser;
import com.springdebugger.tap.RunConsoleTap;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.ui.DiagnosisCardPanel;
import com.springdebugger.rule.RuleCatalog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Monitors a chosen terminal tab for Spring Boot errors. A terminal has no ProcessHandler and
 * no output events, so this polls the terminal's text buffer on a schedule, diffs out the new
 * output, and when that new output carries an error signature it runs the same diagnosis
 * pipeline the run/test taps use. The user selects which terminal instance to monitor.
 *
 * <p>Project-scoped: only one terminal is monitored at a time per project. Duplicate diagnoses
 * are suppressed so a steady error on screen is not re-shown every poll.
 */
@Service(Service.Level.PROJECT)
public final class TerminalMonitorService {

    private static final long POLL_MS = 1500;

    private final Project project;
    private final TerminalTextDiffer differ = new TerminalTextDiffer();
    private final Set<String> shownKeys = new HashSet<>();
    private volatile JBTerminalWidget widget;
    private volatile ScheduledFuture<?> task;

    public TerminalMonitorService(Project project) {
        this.project = project;
    }

    public static TerminalMonitorService getInstance(Project project) {
        return project.getService(TerminalMonitorService.class);
    }

    public synchronized void monitor(JBTerminalWidget target) {
        stop();
        this.widget = target;
        this.differ.reset();
        synchronized (shownKeys) {
            this.shownKeys.clear();
        }
        this.task = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(this::poll, POLL_MS, POLL_MS, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        widget = null;
    }

    public boolean isMonitoring() {
        return task != null && widget != null;
    }

    public JBTerminalWidget monitoredWidget() {
        return widget;
    }

    private void poll() {
        JBTerminalWidget current = widget;
        if (current == null || project.isDisposed()) return;

        String full;
        try {
            full = TerminalReader.read(current);
        } catch (Throwable t) {
            // The terminal may have been closed mid-read; stop quietly.
            stop();
            return;
        }

        String delta = differ.newText(full);
        if (delta.isEmpty() || !RunConsoleTap.containsErrorSignature(delta)) return;

        RuleCatalog catalog = SpringDebuggerService.getInstance().getCatalog();
        List<DiagnosisCard> cards = new ConsoleDiagnoser(catalog).diagnoseAll(full, project);
        boolean firstOfBurst = true;
        for (DiagnosisCard card : cards) {
            synchronized (shownKeys) {
                if (!shownKeys.add(card.groupingKey())) continue;
            }
            DiagnosisCardPanel.show(project, card, !firstOfBurst);
            firstOfBurst = false;
        }
    }
}
