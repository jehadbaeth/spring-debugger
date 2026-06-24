package com.springdebugger.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.springdebugger.SpringDebuggerService;
import com.springdebugger.extractor.TestResultsLocator;
import com.springdebugger.extractor.TestResultsParser;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.settings.SpringDebuggerSettings;
import com.springdebugger.tap.ConsoleDiagnoser;
import com.springdebugger.ui.DiagnosisCardPanel;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Watches the project's JUnit result files (Gradle {@code build/test-results}, Maven
 * {@code surefire-reports}) and diagnoses new test failures. This is the terminal-agnostic path:
 * a developer running {@code ./gradlew test} in any terminal (classic or new) writes these files,
 * so monitoring needs no hook into the terminal at all.
 *
 * <p>Polls rather than using VFS events because Gradle's {@code build/} directory is excluded in
 * most IntelliJ projects, so VFS does not reliably fire there. On start it baselines the current
 * files so pre-existing results from before the IDE was opened are not re-surfaced; only files
 * written after that are diagnosed.
 */
@Service(Service.Level.PROJECT)
public final class TestResultsWatchService {

    private static final long POLL_MS = 4000;
    private static final com.intellij.openapi.diagnostic.Logger LOG =
            com.intellij.openapi.diagnostic.Logger.getInstance(TestResultsWatchService.class);

    private final Project project;
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    private volatile ScheduledFuture<?> task;

    public TestResultsWatchService(Project project) {
        this.project = project;
    }

    public static TestResultsWatchService getInstance(Project project) {
        return project.getService(TestResultsWatchService.class);
    }

    public synchronized void start() {
        if (task != null) return;
        baseline();
        task = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(this::poll, POLL_MS, POLL_MS, TimeUnit.MILLISECONDS);
        LOG.info("TestResultsWatchService started for " + project.getName());
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    public boolean isWatching() {
        return task != null;
    }

    /** Records current files and their timestamps without diagnosing, so only later runs surface. */
    private void baseline() {
        lastSeen.clear();
        for (File f : locate()) {
            lastSeen.put(f.getPath(), f.lastModified());
        }
    }

    private void poll() {
        if (project.isDisposed() || !SpringDebuggerSettings.getInstance().isWatchTestResults()) return;
        try {
            // 1) Find files written since the last poll (new path, or newer timestamp).
            List<File> changed = new java.util.ArrayList<>();
            for (File f : locate()) {
                long modified = f.lastModified();
                Long previous = lastSeen.put(f.getPath(), modified);
                if (previous == null || previous != modified) {
                    changed.add(f);
                }
            }
            if (changed.isEmpty()) return;

            // 2) Diagnose only the changed files. One test run rewrites many suite files at once,
            // so the dedup set spans the whole batch and balloons only the first of the burst.
            Set<String> shown = new HashSet<>();
            boolean firstOfBurst = true;
            ConsoleDiagnoser diagnoser = new ConsoleDiagnoser(SpringDebuggerService.getInstance().getCatalog());
            for (File f : changed) {
                String content = read(f);
                if (content == null || !TestResultsParser.hasFailures(content)) continue;
                for (String failure : TestResultsParser.failureTexts(content)) {
                    for (DiagnosisCard card : diagnoser.diagnoseAll(failure, project)) {
                        if (!shown.add(card.groupingKey())) continue;
                        DiagnosisCardPanel.show(project, card, !firstOfBurst);
                        firstOfBurst = false;
                    }
                }
            }
        } catch (Throwable t) {
            LOG.warn("TestResultsWatchService poll failed", t);
        }
    }

    private List<File> locate() {
        String base = project.getBasePath();
        return base == null ? List.of() : TestResultsLocator.locate(new File(base));
    }

    private String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
