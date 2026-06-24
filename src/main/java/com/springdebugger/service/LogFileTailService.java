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
 * <p>Watches <b>every</b> log file declared via {@code logging.file.name}/{@code logging.file}
 * across the project (one per service in a multi-module build), or a single explicit
 * {@code logFilePath} when set. The set is <b>re-discovered periodically</b>, not fixed at startup,
 * so committing the property and running works without restarting the IDE, and log files that only
 * appear when a service is first run are picked up automatically.
 *
 * <p>Each file is followed by byte offset and reset on truncation/rotation; per-file tail buffers
 * keep the end so a late error survives. Polls because the files live under {@code build/}, which
 * IntelliJ excludes from VFS.
 */
@Service(Service.Level.PROJECT)
public final class LogFileTailService {

    private static final long POLL_MS = 1500;
    /** Re-resolve the watched-file set every this many polls (~9s) to catch newly added configs/logs. */
    private static final int REDISCOVER_EVERY = 6;
    private static final int BUFFER_MAX_CHARS = 200_000;
    private static final com.intellij.openapi.diagnostic.Logger LOG =
            com.intellij.openapi.diagnostic.Logger.getInstance(LogFileTailService.class);

    private final Project project;
    private final Map<String, File> watched = new ConcurrentHashMap<>();
    private final Map<String, Long> offsets = new ConcurrentHashMap<>();
    private final Map<String, StringBuilder> buffers = new ConcurrentHashMap<>();
    private final Set<String> shownKeys = ConcurrentHashMap.newKeySet();
    private volatile int sinceDiscovery;
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

    /** Starts watching. Safe to call when nothing is configured yet: it keeps re-discovering. */
    public synchronized void start() {
        if (task != null) return;
        watched.clear();
        offsets.clear();
        buffers.clear();
        shownKeys.clear();
        sinceDiscovery = 0;
        // Initial set: baseline existing files at EOF so old log content is not replayed on open.
        for (File f : resolveLogFiles()) {
            String key = f.getPath();
            watched.put(key, f);
            offsets.put(key, f.exists() ? f.length() : 0L);
        }
        LOG.info("LogFileTailService starting, initial files=" + watched.keySet());
        this.task = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(this::poll, POLL_MS, POLL_MS, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        watched.clear();
    }

    public boolean isTailing() {
        return task != null;
    }

    public List<File> tailedFiles() {
        return List.copyOf(watched.values());
    }

    private void poll() {
        if (project.isDisposed() || !SpringDebuggerSettings.getInstance().isWatchLogFile()) return;
        if (++sinceDiscovery >= REDISCOVER_EVERY) {
            sinceDiscovery = 0;
            rediscover();
        }
        for (File f : watched.values()) {
            try {
                tailOne(f);
            } catch (Throwable t) {
                LOG.warn("LogFileTailService poll failed for " + f, t);
            }
        }
    }

    /** Adds files declared since start. A newly-appearing file is read from the beginning (offset 0). */
    private void rediscover() {
        for (File f : resolveLogFiles()) {
            String key = f.getPath();
            if (watched.putIfAbsent(key, f) == null) {
                offsets.put(key, 0L); // new to us: read it fully (it is a fresh run's log)
                LOG.info("LogFileTailService now watching " + key);
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
        LOG.info("LogFileTailService diagnosed " + key + " -> " + cards.size() + " card(s)");
        boolean firstOfBurst = true;
        for (DiagnosisCard card : cards) {
            if (!shownKeys.add(card.groupingKey())) continue;
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
