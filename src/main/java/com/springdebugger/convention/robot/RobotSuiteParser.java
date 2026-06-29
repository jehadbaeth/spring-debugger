package com.springdebugger.convention.robot;

import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure, line-based parser for a Robot Framework suite file. No PSI, no IO: text in, {@link
 * RobotSuite} out, with absolute text offsets for highlighting.
 *
 * <p>Handles the Robot realities the checks care about: section headers are {@code *** Name ***}
 * (case-insensitive, any run of asterisks/spaces); the cell separator is two-or-more spaces or a
 * tab; full-line {@code #} comments are skipped; {@code ...} continuation lines extend a preceding
 * {@code [Tags]}. It is intentionally shallow: it does not execute keywords or resolve variables.
 */
public final class RobotSuiteParser {

    private static final Pattern HEADER = Pattern.compile("^\\*+\\s*(.+?)\\s*\\*+\\s*$");
    private static final Pattern SEPARATOR = Pattern.compile("(?: {2,}|\\t+)");

    private RobotSuiteParser() {}

    public static RobotSuite parse(String text) {
        List<RobotSuite.Metadata> metadata = new ArrayList<>();
        List<RobotSuite.TestCase> testCases = new ArrayList<>();
        boolean hasTestCasesSection = false;
        TextRange settingsHeaderRange = null;

        String[] lines = text.split("\n", -1);
        int lineStart = 0;
        String section = null;
        RobotSuite.TestCase current = null;
        boolean inTagsContinuation = false;

        for (String raw : lines) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            int start = lineStart;
            lineStart += raw.length() + 1; // +1 for the split-removed '\n'

            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String header = headerName(trimmed);
            if (header != null) {
                if (header.startsWith("setting")) {
                    section = "SETTINGS";
                    settingsHeaderRange = contentRange(line, start);
                } else if (header.startsWith("test case") || header.startsWith("task")) {
                    section = "TEST_CASES";
                    hasTestCasesSection = true;
                } else if (header.startsWith("variable")) {
                    section = "VARIABLES";
                } else if (header.startsWith("keyword")) {
                    section = "KEYWORDS";
                } else {
                    section = "OTHER";
                }
                current = null;
                inTagsContinuation = false;
                continue;
            }

            if (trimmed.startsWith("#")) continue;

            if ("SETTINGS".equals(section)) {
                List<String> tokens = splitCells(trimmed);
                if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("Metadata")) {
                    String name = tokens.get(1);
                    String value = tokens.size() >= 3
                            ? String.join(" ", tokens.subList(2, tokens.size())) : "";
                    metadata.add(new RobotSuite.Metadata(name, value, contentRange(line, start)));
                }
            } else if ("TEST_CASES".equals(section)) {
                boolean indented = !line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t');
                if (!indented) {
                    current = new RobotSuite.TestCase(trimmed, contentRange(line, start));
                    testCases.add(current);
                    inTagsContinuation = false;
                } else if (current != null) {
                    List<String> tokens = splitCells(trimmed);
                    String first = tokens.isEmpty() ? "" : tokens.get(0);
                    if (first.equalsIgnoreCase("[Documentation]")) {
                        current.hasDocumentation = true;
                        inTagsContinuation = false;
                    } else if (first.equalsIgnoreCase("[Tags]")) {
                        current.hasTags = true;
                        addTags(current, tokens, 1);
                        inTagsContinuation = true;
                    } else if (first.equals("...") && inTagsContinuation) {
                        addTags(current, tokens, 1);
                    } else {
                        inTagsContinuation = false;
                    }
                }
            }
        }
        return new RobotSuite(settingsHeaderRange, metadata, testCases, hasTestCasesSection);
    }

    private static String headerName(String trimmed) {
        var m = HEADER.matcher(trimmed);
        return m.matches() ? m.group(1).toLowerCase(Locale.ROOT) : null;
    }

    private static List<String> splitCells(String trimmed) {
        List<String> out = new ArrayList<>();
        for (String tok : SEPARATOR.split(trimmed)) {
            if (!tok.isEmpty()) out.add(tok);
        }
        return out;
    }

    private static void addTags(RobotSuite.TestCase tc, List<String> tokens, int from) {
        for (int i = from; i < tokens.size(); i++) tc.tags.add(tokens.get(i));
    }

    /** Absolute range covering the trimmed content of a line (first to last non-whitespace char). */
    private static TextRange contentRange(String line, int lineStart) {
        int begin = 0;
        while (begin < line.length() && Character.isWhitespace(line.charAt(begin))) begin++;
        int end = line.length();
        while (end > begin && Character.isWhitespace(line.charAt(end - 1))) end--;
        if (begin >= end) return new TextRange(lineStart, lineStart + Math.max(0, line.length()));
        return new TextRange(lineStart + begin, lineStart + end);
    }
}
