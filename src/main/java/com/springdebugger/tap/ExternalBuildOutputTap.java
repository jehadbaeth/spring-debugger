package com.springdebugger.tap;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.project.Project;
import com.springdebugger.SpringDebuggerService;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.rule.RuleCatalog;
import com.springdebugger.ui.DiagnosisCardPanel;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Build tap for delegated Gradle and Maven runs, which is the default build/run path for most
 * Spring Boot projects. IntelliJ streams the build tool's stdout/stderr through the
 * external-system notification bus (this is what fires when you press a task in the Gradle or
 * Maven tool window, or run a delegated build); this listener buffers that output per task and
 * analyses it when the task ends.
 *
 * <p>It diagnoses all three shapes that show up here: a compile failure (gradle build), an
 * application startup failure (gradle bootRun / spring-boot:run), and a runtime exception
 * (including Kafka). It tries STARTUP, then COMPILE, then RUNTIME and shows the first match.
 *
 * <p>Registered app-level via the {@code externalSystemTaskNotificationListener} extension
 * point. Only EXECUTE_TASK output is buffered; project resolve/sync output is ignored.
 */
public final class ExternalBuildOutputTap extends ExternalSystemTaskNotificationListenerAdapter {

    /** Hard cap so a noisy build cannot grow a buffer without bound. */
    private static final int BUFFER_MAX_CHARS = 300_000;

    private final Map<ExternalSystemTaskId, StringBuilder> buffers = new ConcurrentHashMap<>();

    @Override
    public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
        if (id.getType() != ExternalSystemTaskType.EXECUTE_TASK) return;
        StringBuilder buffer = buffers.computeIfAbsent(id, k -> new StringBuilder());
        if (buffer.length() < BUFFER_MAX_CHARS) {
            buffer.append(text);
        }
    }

    @Override
    public void onEnd(@NotNull ExternalSystemTaskId id) {
        String output = drainBuffer(id);
        if (output == null) return;

        Project project = id.findProject();
        if (project == null) return;

        RuleCatalog catalog = SpringDebuggerService.getInstance().getCatalog();
        analyse(output, project, catalog).ifPresent(c -> DiagnosisCardPanel.show(project, c));
    }

    /**
     * Diagnoses delegated build/run output. Tries the application-startup path first (this is
     * where bootRun failures land), then the compile path, then a generic runtime pass that
     * catches runtime and Kafka exceptions. Package-visible and project-tolerant so the core
     * can be unit-tested; PSI/Actuator enrichment only engages when a real project is given.
     */
    Optional<DiagnosisCard> analyse(String output, Project project, RuleCatalog catalog) {
        return new ConsoleDiagnoser(catalog).diagnose(output, project);
    }

    /**
     * Removes and returns the buffered output for a finished task, or null when there is
     * nothing to analyse. Package-visible so the buffering/filtering can be unit-tested
     * without the IDE-coupled {@code findProject}/{@code show} steps.
     */
    String drainBuffer(ExternalSystemTaskId id) {
        StringBuilder buffer = buffers.remove(id);
        if (buffer == null || buffer.length() == 0) return null;
        if (id.getType() != ExternalSystemTaskType.EXECUTE_TASK) return null;
        return buffer.toString();
    }
}
