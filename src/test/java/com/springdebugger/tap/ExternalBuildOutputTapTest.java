package com.springdebugger.tap;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the external (Gradle/Maven) build tap's own buffering and filtering, which is the
 * part of M6 that does not need a live IDE. The actual diagnosis is covered by
 * {@link BuildOutputAnalyzerTest}; event delivery from the IDE to the registered listener is
 * the one remaining manual sandbox check.
 */
class ExternalBuildOutputTapTest {

    private static final ProjectSystemId GRADLE = new ProjectSystemId("GRADLE");

    private static ExternalSystemTaskId taskId(ExternalSystemTaskType type, String id) {
        return ExternalSystemTaskId.create(GRADLE, type, id);
    }

    @Test
    void buffersExecuteTaskOutputAndDrainsItOnce() {
        ExternalBuildOutputTap tap = new ExternalBuildOutputTap();
        ExternalSystemTaskId id = taskId(ExternalSystemTaskType.EXECUTE_TASK, "exec-1");

        tap.onTaskOutput(id, "Unmapped target property: \"x\"\n", true);
        tap.onTaskOutput(id, "more build output\n", true);

        String drained = tap.drainBuffer(id);
        assertThat(drained).contains("Unmapped target property").contains("more build output");
        // Draining is one-shot: the buffer is cleared.
        assertThat(tap.drainBuffer(id)).isNull();
    }

    @Test
    void ignoresNonExecuteTaskOutput() {
        ExternalBuildOutputTap tap = new ExternalBuildOutputTap();
        ExternalSystemTaskId resolveId = taskId(ExternalSystemTaskType.RESOLVE_PROJECT, "resolve-1");

        tap.onTaskOutput(resolveId, "project import noise\n", true);

        assertThat(tap.drainBuffer(resolveId)).isNull();
    }

    @Test
    void drainOfUnknownTaskIsNull() {
        ExternalBuildOutputTap tap = new ExternalBuildOutputTap();
        assertThat(tap.drainBuffer(taskId(ExternalSystemTaskType.EXECUTE_TASK, "never-seen"))).isNull();
    }
}
