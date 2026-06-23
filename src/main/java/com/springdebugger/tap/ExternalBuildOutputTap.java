package com.springdebugger.tap;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.project.Project;
import com.springdebugger.SpringDebuggerService;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.ui.DiagnosisCardPanel;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Build tap for delegated Gradle and Maven builds, which is the default build path for
 * most Spring Boot projects. IntelliJ streams the build tool's stdout/stderr through the
 * external-system notification bus; this listener buffers that output per running task and,
 * when the task ends, hands the buffer to {@link BuildOutputAnalyzer}.
 *
 * <p>Registered app-level via the {@code com.intellij.externalSystemTaskNotificationListener}
 * extension point in plugin.xml. Only EXECUTE_TASK output is buffered (a real build run);
 * project resolve/sync output is ignored.
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

        BuildOutputAnalyzer analyzer = new BuildOutputAnalyzer(
                SpringDebuggerService.getInstance().getCatalog());
        Optional<DiagnosisCard> card = analyzer.analyze(output);
        card.ifPresent(c -> DiagnosisCardPanel.show(project, c));
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
