package com.springdebugger.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.springdebugger.SpringDebuggerService;
import com.springdebugger.extractor.LogFilePropertyFinder;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.settings.SpringDebuggerSettings;
import com.springdebugger.tap.ConsoleDiagnoser;
import com.springdebugger.tap.RunConsoleTap;
import com.springdebugger.ui.DiagnosisCardPanel;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tails Spring Boot log files and diagnoses errors as they are appended. This is the
 * terminal-agnostic capture path for {@code bootRun} (and any java process) started in a terminal:
 * the app's own log file is read directly, so no hook into the terminal is needed.
 *
 * <p>Watches <b>every</b> log file declared via {@code logging.file.name} across the project (one
 * per service in a multi-module build), or a single explicit {@code logFilePath} when set. Each
 * file is followed by byte offset and reset on truncation/rotation; per-file tail buffers keep the
 * end so a late error survives. Polls because the files live under {@code build/}, which IntelliJ
 * excludes from VFS.
 */
@Service(Service.Level.PROJECT)
public final class LogFileTailService {

    private static final long POLL_MS = 1500;
    private static final int BUFFER_MAX_CHARS = 200_000;
    private static final com.intellij.openapi.diagnostic.Logger LOG =
            com.intellij.openapi.diagnostic.Logger.getInstance(LogFileTailService.class);

    private final Project project;
    private final Map<String, Long> offsets = new ConcurrentHashMap<>();
    private final Map<String, StringBuilder> buffers = new ConcurrentHashMap<>();
    private final Set<String> shownKeys = new HashSet<>();
    private volatile List<File> files = List.of();
    private volatile ScheduledFuture<?> task;

    public LogFileTailService(Project project) {
        this.project = project;
    }

    public static LogFileTailService getInstance(Project project) {
        return project.getService(LogFileTailService.class);
    }

    /**
     * Resolves the log files to watch: a single explicit {@code logFilePath} if set, otherwise every
     * {@code logging.file.name} discovered across the project's application configs.
     */
    public List<File> resolveLogFiles() {
        String base = project.getBasePath();
        String configured = SpringDebuggerSettings.getInstance().getLogFilePath();
        if (configured != null && !configured.isBlank()) {
            File f = new File(configured);
            return List.of(f.isAbsolute() || base == null ? f : new File(base, configured));
        }
        return base == null ? List.of() : LogFilePropertyFinder.discoverAll(new File(base));
    }

    public synchronized void start(List<File> targets) {
        stop();
        if (targets == null || targets.isEmpty()) return;
        this.files = List.copyOf(targets);
        offsets.clear();
        buffers.clear();
        synchronized (shownKeys) { shownKeys.clear(); }
        for (File f : files) {
            // Start at the current end so only output written from now on is diagnosed.
            offsets.put(f.getPath(), f.exists() ? f.length() : 0L);
        }
        this.task = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(this::poll, POLL_MS, POLL_MS, TimeUnit.MILLISECONDS);
        LOG.info("LogFileTailService tailing " + files.size() + " file(s): " + files);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        files = List.of();
    }

    public boolean isTailing() {
        return task != null && !files.isEmpty();
    }

    public List<File> tailedFiles() {
        return files;
    }

    private void poll() {
        if (project.isDisposed() || !SpringDebuggerSettings.getInstance().isWatchLogFile()) return;
        for (File f : files) {
            try {
                tailOne(f);
            } catch (Throwable t) {
                LOG.warn("LogFileTailService poll failed for " + f, t);
            }
        }
    }

    private void tailOne(File current) throws Exception {
        if (!current.exists()) return;
        String key = current.getPath();
        long previous = offsets.getOrDefault(key, 0L);
        long length = current.length();
        if (length < previous) {
            // Truncated or rotated: restart from the beginning of the new file.
            previous = 0;
            buffers.remove(key);
        }
        if (length == previous) return;

        String delta = readFrom(current, previous, length);
        offsets.put(key, length);
        if (delta.isEmpty()) return;

        StringBuilder buffer = buffers.computeIfAbsent(key, k -> new StringBuilder());
        buffer.append(delta);
        if (buffer.length() > BUFFER_MAX_CHARS) {
            buffer.delete(0, buffer.length() - BUFFER_MAX_CHARS);
        }
        if (!RunConsoleTap.containsErrorSignature(delta)) return;

        List<DiagnosisCard> cards = new ConsoleDiagnoser(SpringDebuggerService.getInstance().getCatalog())
                .diagnoseAll(buffer.toString(), project);
        boolean firstOfBurst = true;
        for (DiagnosisCard card : cards) {
            synchronized (shownKeys) {
                if (!shownKeys.add(card.groupingKey())) continue;
            }
            DiagnosisCardPanel.show(project, card, !firstOfBurst);
            firstOfBurst = false;
        }
    }

    private String readFrom(File f, long from, long to) throws Exception {
        long len = to - from;
        if (len <= 0) return "";
        if (len > BUFFER_MAX_CHARS) {
            from = to - BUFFER_MAX_CHARS;
            len = BUFFER_MAX_CHARS;
        }
        byte[] bytes = new byte[(int) len];
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            raf.seek(from);
            raf.readFully(bytes);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
