package com.springdebugger.tap;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.rule.RuleCatalog;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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

    // ── analyse(): the three shapes that arrive from the Gradle/Maven tool window ──
    // Driven with a null project so no IDE is needed (enrichment simply does not engage).

    private final ExternalBuildOutputTap tap = new ExternalBuildOutputTap();
    private final RuleCatalog catalog = RuleCatalog.load();

    @Test
    void diagnosesCompileFailureFromGradleBuild() {
        String out = "> Task :compileJava FAILED\n"
                + "/src/main/java/com/example/mapper/UserMapper.java:15: error: Unmapped target property: \"createdAt\".\n"
                + "1 error\nBUILD FAILED";
        Optional<DiagnosisCard> card = tap.analyse(out, null, catalog);
        assertThat(card).isPresent();
        assertThat(card.get().getRuleId()).isEqualTo("13.1");
    }

    @Test
    void diagnosesStartupFailureFromBootRun() {
        String out = "> Task :bootRun\n"
                + "***************************\nAPPLICATION FAILED TO START\n***************************\n\n"
                + "java.lang.IllegalStateException: Failed to instantiate context\n"
                + "Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: "
                + "No qualifying bean of type 'com.example.OrderService' available\n";
        Optional<DiagnosisCard> card = tap.analyse(out, null, catalog);
        assertThat(card).isPresent();
        assertThat(card.get().getRuleId()).isEqualTo("2.1");
    }

    @Test
    void diagnosesKafkaRuntimeFailureFromBootRun() {
        String out = "> Task :bootRun\n"
                + "org.apache.kafka.common.errors.TimeoutException: Failed to update metadata after 60000 ms.\n";
        Optional<DiagnosisCard> card = tap.analyse(out, null, catalog);
        assertThat(card).isPresent();
        assertThat(card.get().getRuleId()).isEqualTo("14.1");
    }

    @Test
    void cleanBuildOutputProducesNoCard() {
        assertThat(tap.analyse("> Task :build\nBUILD SUCCESSFUL in 3s\n", null, catalog)).isEmpty();
    }
}
