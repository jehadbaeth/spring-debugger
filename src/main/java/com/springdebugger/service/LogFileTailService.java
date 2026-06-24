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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tails a Spring Boot log file and diagnoses errors as they are appended. This is the
 * terminal-agnostic capture path for {@code bootRun} (and any java process) started in a terminal:
 * the app's own log file is read directly, so no hook into the terminal is needed.
 *
 * <p>The file path comes from settings ({@code logFilePath}) or, when blank, is auto-discovered from
 * {@code logging.file.name} in the project's {@code application*.properties/yml}. If the app logs to
 * the console only, the user sets either one. The reader follows appends by byte offset and resets
 * on truncation/rotation; the in-memory buffer keeps a tail so a late error survives.
 */
@Service(Service.Level.PROJECT)
public final class LogFileTailService {

    private static final long POLL_MS = 1500;
    private static final int BUFFER_MAX_CHARS = 200_000;
    private static final com.intellij.openapi.diagnostic.Logger LOG =
            com.intellij.openapi.diagnostic.Logger.getInstance(LogFileTailService.class);

    private final Project project;
    private final StringBuilder buffer = new StringBuilder();
    private final Set<String> shownKeys = new HashSet<>();
    private volatile File file;
    private volatile long offset;
    private volatile ScheduledFuture<?> task;

    public LogFileTailService(Project project) {
        this.project = project;
    }

    public static LogFileTailService getInstance(Project project) {
        return project.getService(LogFileTailService.class);
    }

    /** Resolves the configured or auto-discovered log file, relative to the project base, or null. */
    public File resolveLogFile() {
        String base = project.getBasePath();
        String configured = SpringDebuggerSettings.getInstance().getLogFilePath();
        if (configured != null && !configured.isBlank()) {
            File f = new File(configured);
            return f.isAbsolute() || base == null ? f : new File(base, configured);
        }
        String discovered = base == null ? null : LogFilePropertyFinder.discover(new File(base));
        if (discovered == null) return null;
        File f = new File(discovered);
        return f.isAbsolute() || base == null ? f : new File(base, discovered);
    }

    public synchronized void start(File target) {
        stop();
        if (target == null) return;
        this.file = target;
        this.offset = target.length(); // start at the end: only diagnose new output from now on
        this.buffer.setLength(0);
        synchronized (shownKeys) { shownKeys.clear(); }
        this.task = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(this::poll, POLL_MS, POLL_MS, TimeUnit.MILLISECONDS);
        LOG.info("LogFileTailService tailing " + target.getPath());
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        file = null;
    }

    public boolean isTailing() {
        return task != null && file != null;
    }

    public File tailedFile() {
        return file;
    }

    private void poll() {
        File current = file;
        if (current == null || project.isDisposed()) return;
        if (!SpringDebuggerSettings.getInstance().isWatchLogFile()) return;
        try {
            long length = current.length();
            if (length < offset) {
                // Truncated or rotated: restart from the beginning of the new file.
                offset = 0;
                buffer.setLength(0);
            }
            if (length == offset) return;

            String delta = readFrom(current, offset, length);
            offset = length;
            if (delta.isEmpty()) return;

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
        } catch (Throwable t) {
            LOG.warn("LogFileTailService poll failed", t);
        }
    }

    private String readFrom(File f, long from, long to) throws Exception {
        long len = to - from;
        if (len <= 0) return "";
        if (len > BUFFER_MAX_CHARS) {
            // A huge burst since last poll: keep only the tail of it.
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
