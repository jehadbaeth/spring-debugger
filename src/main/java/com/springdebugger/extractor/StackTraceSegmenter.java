package com.springdebugger.extractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits a noisy log buffer into individual error blocks so each can be diagnosed on its own.
 * An integration run (e.g. a Robot Framework suite hitting many endpoints) can throw several
 * distinct errors into one log; the rest of the engine only ever pulled the single deepest
 * cause from the whole buffer, collapsing them to one.
 *
 * <p>A line starts a new block when it is either a top-level exception printed at column zero
 * (a fully-qualified class ending in Exception/Error) or a one-line Spring MVC error the
 * dispatcher logs without a stack: a resolved handler exception ("Resolved [...]") or a missing
 * handler ("No endpoint ..."). "Caused by:" lines and "\tat" frames stay inside their block.
 *
 * <p>If fewer than two boundaries are found the whole text is returned unchanged, which
 * preserves the existing single-error and banner-only behaviour exactly.
 */
public final class StackTraceSegmenter {

    private static final Pattern TOP_LEVEL =
            Pattern.compile("^([\\w.$]+(?:Exception|Error))(?::|\\s|$)");

    private StackTraceSegmenter() {}

    public static List<String> segment(String text) {
        if (text == null || text.isBlank()) return List.of();

        String[] lines = text.split("\n", -1);
        List<Integer> boundaries = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (isBoundary(lines[i])) boundaries.add(i);
        }
        if (boundaries.size() <= 1) {
            return List.of(text);
        }

        List<String> regions = new ArrayList<>(boundaries.size());
        for (int k = 0; k < boundaries.size(); k++) {
            int from = boundaries.get(k);
            int to = (k + 1 < boundaries.size()) ? boundaries.get(k + 1) : lines.length;
            regions.add(String.join("\n", Arrays.copyOfRange(lines, from, to)));
        }
        return regions;
    }

    private static boolean isBoundary(String line) {
        if (TOP_LEVEL.matcher(line).find()) return true;
        // One-line MVC errors the dispatcher logs without a stack trace.
        return line.contains("Resolved [") || line.contains("No endpoint ");
    }
}
