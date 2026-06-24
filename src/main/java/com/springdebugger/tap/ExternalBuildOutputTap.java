package com.springdebugger.tap;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.springdebugger.SpringDebuggerService;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.rule.RuleCatalog;
import com.springdebugger.ui.DiagnosisCardPanel;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Build/run tap for delegated Gradle and Maven tasks, the default path for most Spring Boot
 * projects. IntelliJ streams the tool's stdout/stderr through the external-system bus (this is
 * what fires when you press a task in the Gradle or Maven tool window); this listener buffers
 * that output per task and diagnoses it.
 *
 * <p>Crucially it analyses <b>mid-run</b>, not only when the task ends: a {@code bootRun} task
 * does not end while the app is up and an integration suite hits it, so waiting for {@code onEnd}
 * would surface nothing. Analysis is debounced per task and surfaces every distinct error
 * (de-duplicated), ballooning only the first of a burst.
 *
 * <p>Registered app-level via the {@code externalSystemTaskNotificationListener} extension point.
 * Only EXECUTE_TASK output is handled; project resolve/sync output is ignored.
 */
public final class ExternalBuildOutputTap extends ExternalSystemTaskNotificationListenerAdapter {

    private static final int BUFFER_MAX_CHARS = 300_000;
    private static final long DEBOUNCE_MS = 1500;

    private static final com.intellij.openapi.diagnostic.Logger LOG =
            com.intellij.openapi.diagnostic.Logger.getInstance(ExternalBuildOutputTap.class);

    private final Map<ExternalSystemTaskId, StringBuilder> buffers = new ConcurrentHashMap<>();
    private final Map<ExternalSystemTaskId, Set<String>> shownKeys = new ConcurrentHashMap<>();
    private final Map<ExternalSystemTaskId, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final Set<ExternalSystemTaskId> announced = ConcurrentHashMap.newKeySet();

    @Override
    public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
        if (id.getType() != ExternalSystemTaskType.EXECUTE_TASK) {
            if (announced.add(id)) {
                LOG.info("ExternalBuildOutputTap saw non-EXECUTE_TASK output: " + id.getType());
            }
            return;
        }
        if (announced.add(id)) {
            LOG.info("ExternalBuildOutputTap receiving EXECUTE_TASK output (first chunk " + text.length() + " chars)");
        }
        StringBuilder buffer = buffers.computeIfAbsent(id, k -> new StringBuilder());
        synchronized (buffer) {
            // Keep the tail rather than dropping past the cap (see RunConsoleTap): a bootRun's late
            // errors arrive after a large startup log and must survive.
            buffer.append(text);
            if (buffer.length() > BUFFER_MAX_CHARS) {
                buffer.delete(0, buffer.length() - BUFFER_MAX_CHARS);
            }
        }
        if (RunConsoleTap.containsErrorSignature(text)) {
            scheduleAnalysis(id);
        }
    }

    @Override
    public void onEnd(@NotNull ExternalSystemTaskId id) {
        ScheduledFuture<?> p = pending.remove(id);
        if (p != null) p.cancel(false);
        analyseTask(id);
        buffers.remove(id);
        shownKeys.remove(id);
        announced.remove(id);
    }

    private void scheduleAnalysis(ExternalSystemTaskId id) {
        ScheduledFuture<?> previous = pending.get(id);
        if (previous != null) previous.cancel(false);
        pending.put(id, AppExecutorUtil.getAppScheduledExecutorService()
                .schedule(() -> analyseTask(id), DEBOUNCE_MS, TimeUnit.MILLISECONDS));
    }

    private void analyseTask(ExternalSystemTaskId id) {
        StringBuilder buffer = buffers.get(id);
        if (buffer == null) return;
        String snapshot;
        synchronized (buffer) {
            snapshot = buffer.toString();
        }
        Project project = id.findProject();
        if (project == null) return;

        Set<String> seen = shownKeys.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
        List<DiagnosisCard> cards = new ConsoleDiagnoser(
                SpringDebuggerService.getInstance().getCatalog()).diagnoseAll(snapshot, project);
        LOG.info("ExternalBuildOutputTap analysed " + snapshot.length() + " chars, produced " + cards.size() + " card(s)");
        boolean firstOfBurst = true;
        for (DiagnosisCard card : cards) {
            if (!seen.add(card.groupingKey())) continue;
            DiagnosisCardPanel.show(project, card, !firstOfBurst);
            firstOfBurst = false;
        }
    }

    /**
     * Diagnoses a chunk of build/run output. Project-tolerant so the core is unit-testable;
     * returns the first distinct diagnosis (the per-error set is exercised via the live path).
     */
    Optional<DiagnosisCard> analyse(String output, Project project, RuleCatalog catalog) {
        List<DiagnosisCard> cards = new ConsoleDiagnoser(catalog).diagnoseAll(output, project);
        return cards.isEmpty() ? Optional.empty() : Optional.of(cards.get(0));
    }

    /** Removes and returns buffered output for a finished task, or null when empty. For tests. */
    String drainBuffer(ExternalSystemTaskId id) {
        StringBuilder buffer = buffers.remove(id);
        if (buffer == null || buffer.length() == 0) return null;
        if (id.getType() != ExternalSystemTaskType.EXECUTE_TASK) return null;
        return buffer.toString();
    }
}
