package com.springdebugger.extractor;

import com.springdebugger.model.Phase;
import com.springdebugger.model.RawSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a raw block of Spring Boot log output and produces a RawSignal.
 * Takes the full buffered excerpt; the taps decide when to trigger extraction.
 */
public final class LogExtractor {

    private static final Pattern CAUSED_BY = Pattern.compile(
            "^\\s*Caused by:\\s*([\\w.$]+):\\s*(.*)$", Pattern.MULTILINE);

    /**
     * Spring's older inline chain style: "...; nested exception is org.x.Y: message".
     * Used only as a fallback when a log has no "Caused by:" lines at all, which is common
     * in real-world logs captured from Spring Boot 1.x / 2.x and from partial paste-ups.
     */
    private static final Pattern NESTED_EXCEPTION = Pattern.compile(
            "nested exception is\\s+([\\w.$]+):\\s*(.*?)(?=;\\s*nested exception is|\\R|$)");

    /**
     * Last-resort: a top-level exception printed at column zero, e.g.
     * "org.yaml.snakeyaml.scanner.ScannerException: while scanning...". Many real runtime
     * errors (404, YAML parse, validation) surface this way with no "Caused by:" chain.
     * Anchored at line start so it never picks up "\tat ..." stack frames; the first such
     * line is the originating exception.
     */
    private static final Pattern TOP_LEVEL_EXCEPTION = Pattern.compile(
            "(?m)^([\\w.$]+(?:Exception|Error)):\\s+(.+)$");

    private static final Pattern BEAN_CREATION_ERROR = Pattern.compile(
            "Error creating bean with name '([^']+)'");

    private static final Pattern BANNER_START = Pattern.compile(
            "^\\*{10,}\\s*$", Pattern.MULTILINE);

    private static final Pattern BANNER_DESCRIPTION = Pattern.compile(
            "(?m)^Description:\\s*\\R+\\s*(.+)$");

    private static final Pattern BANNER_ACTION = Pattern.compile(
            "(?m)^Action:\\s*\\R+\\s*(.+)$");

    private static final Pattern HTTP_STATUS = Pattern.compile(
            "(?:ResponseStatus|status=|HTTP/)\\s*(\\d{3})");

    private static final Pattern BUILD_ERROR_LINE = Pattern.compile(
            "(?i)(?:error:|cannot find symbol|compilation failed|build failed)(.*)");

    public RawSignal extract(String rawText, Phase phase) {
        String[] lines = rawText.split("\\R");

        String deepestCausedByClass = null;
        String deepestCausedByMessage = null;
        String bannerDescription = extractBannerSection(rawText, "Description:");
        String bannerAction = extractBannerSection(rawText, "Action:");
        String failingBeanName = null;
        int httpStatus = -1;
        List<String> relevantLines = new ArrayList<>();

        Matcher causedByMatcher = CAUSED_BY.matcher(rawText);
        while (causedByMatcher.find()) {
            deepestCausedByClass = causedByMatcher.group(1).trim();
            deepestCausedByMessage = causedByMatcher.group(2).trim();
        }

        // Fallback for logs that use the inline "nested exception is" style with no
        // "Caused by:" lines. The canonical "Caused by:" chain always wins when present.
        if (deepestCausedByClass == null) {
            Matcher nestedMatcher = NESTED_EXCEPTION.matcher(rawText);
            while (nestedMatcher.find()) {
                deepestCausedByClass = nestedMatcher.group(1).trim();
                deepestCausedByMessage = nestedMatcher.group(2).trim();
            }
        }

        // Last resort: a bare top-level exception line with no cause chain at all.
        if (deepestCausedByClass == null) {
            Matcher topMatcher = TOP_LEVEL_EXCEPTION.matcher(rawText);
            if (topMatcher.find()) {
                deepestCausedByClass = topMatcher.group(1).trim();
                deepestCausedByMessage = topMatcher.group(2).trim();
            }
        }

        Matcher beanMatcher = BEAN_CREATION_ERROR.matcher(rawText);
        if (beanMatcher.find()) {
            failingBeanName = beanMatcher.group(1);
        }

        Matcher httpMatcher = HTTP_STATUS.matcher(rawText);
        if (httpMatcher.find()) {
            try { httpStatus = Integer.parseInt(httpMatcher.group(1)); } catch (NumberFormatException ignored) {}
        }

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (isRelevantLine(trimmed)) {
                relevantLines.add(trimmed);
            }
        }

        int excerptStart = Math.max(0, rawText.length() - 8000);
        String excerpt = rawText.substring(excerptStart);

        return new RawSignal(
                phase,
                deepestCausedByClass,
                deepestCausedByMessage,
                bannerDescription,
                bannerAction,
                failingBeanName,
                httpStatus,
                relevantLines,
                excerpt);
    }

    private String extractBannerSection(String text, String sectionHeader) {
        int idx = text.indexOf(sectionHeader);
        if (idx < 0) return null;
        int contentStart = idx + sectionHeader.length();
        while (contentStart < text.length()
                && (text.charAt(contentStart) == '\n'
                    || text.charAt(contentStart) == '\r'
                    || text.charAt(contentStart) == ' ')) {
            contentStart++;
        }
        int end = text.indexOf("\n\n", contentStart);
        if (end < 0) end = Math.min(contentStart + 500, text.length());
        return text.substring(contentStart, end).strip();
    }

    private boolean isRelevantLine(String line) {
        return line.contains("Exception")
                || line.contains("Error")
                || line.contains("Caused by")
                || line.contains("Description:")
                || line.contains("Action:")
                || line.contains("Failed")
                || line.contains("Cannot")
                || line.contains("Unable")
                || line.contains("No qualifying bean")
                || line.contains("required a bean")
                || line.contains("required a single bean")
                || line.contains("Port")
                || line.contains("could not")
                || line.contains("No handler")
                || line.contains("Ambiguous")
                || line.contains("Unmapped")
                || line.contains("Cannot determine")
                || line.contains("MapperImpl")
                || line.contains("cycle")
                || line.contains("circular")
                || BUILD_ERROR_LINE.matcher(line).find();
    }
}
