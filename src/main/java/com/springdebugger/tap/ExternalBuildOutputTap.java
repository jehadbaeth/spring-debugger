package com.springdebugger.tap;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.project.Project;
import com.springdebugger.SpringDebuggerService;
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

    private final LogExtractor extractor = new LogExtractor();
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
        IdeEnrichmentContext context = project != null ? new IdeEnrichmentContext(project) : null;
        DiagnosisPipeline pipeline = new DiagnosisPipeline(
                new RuleBasedClassifier(catalog),
                List.of(new PsiEnricher(), new ActuatorEnricher(), new PropertyPrecedenceEnricher()),
                LlmFallback.fromSettings());

        // 1) Application startup failure (gradle bootRun / spring-boot:run). Tried first
        //    because it is the common case; the phase filter lets compile/runtime-only rules
        //    fall through to steps 2 and 3 when they do not apply at STARTUP.
        Optional<DiagnosisCard> startup = pipeline.run(extractor.extract(output, Phase.STARTUP), context);
        if (startup.isPresent()) return startup;

        // 2) Compile failure (gradle build): the build analyzer applies its Spring-marker gate.
        Optional<DiagnosisCard> compile = new BuildOutputAnalyzer(catalog).analyze(output);
        if (compile.isPresent()) return compile;

        // 3) Runtime / Kafka exception from a running task.
        return pipeline.run(extractor.extract(output, Phase.RUNTIME), context);
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
