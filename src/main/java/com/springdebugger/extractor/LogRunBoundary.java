package com.springdebugger.extractor;

/**
 * Detects Spring Boot run boundaries inside an appending log file. Because the default file appender
 * does not truncate between runs, one {@code app.log} accumulates every {@code bootRun} (and every
 * devtools restart). To avoid surfacing stale errors from a previous run, and to let an identical
 * error re-surface on a re-run, the tailer diagnoses only the slice belonging to the most recent run.
 *
 * <p>The marker is the line Spring logs at the start of every run, e.g.
 * {@code ... : Starting FgAnalysisServiceApplication using Java 21 with PID 129265}. It is present
 * even with the banner disabled and is re-logged on each devtools restart. Pure and unit-tested.
 */
public final class LogRunBoundary {

    private LogRunBoundary() {}

    /** True if the line is a Spring Boot application start line. */
    public static boolean isRunStart(String line) {
        if (line == null) return false;
        return line.contains("Starting ") && line.contains("with PID");
    }

    /** True if any line in the chunk starts a new run. */
    public static boolean containsRunStart(String text) {
        if (text == null || text.isEmpty()) return false;
        for (String line : text.split("\n", -1)) {
            if (isRunStart(line)) return true;
        }
        return false;
    }

    /**
     * Returns the text from the last run-start line onward, i.e. only the most recent run. If no
     * run-start line is present (a non-Boot log, or the marker has scrolled out of a trimmed
     * buffer) the whole text is returned unchanged.
     */
    public static String lastRunSlice(String text) {
        if (text == null || text.isEmpty()) return text;
        String[] lines = text.split("\n", -1);
        int lastStart = -1;
        for (int i = 0; i < lines.length; i++) {
            if (isRunStart(lines[i])) lastStart = i;
        }
        if (lastStart <= 0) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = lastStart; i < lines.length; i++) {
            if (i > lastStart) sb.append('\n');
            sb.append(lines[i]);
        }
        return sb.toString();
    }
}
