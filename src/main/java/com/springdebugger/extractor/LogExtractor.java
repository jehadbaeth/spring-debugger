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
                || line.contains("Port")
                || line.contains("could not")
                || BUILD_ERROR_LINE.matcher(line).find();
    }
}
