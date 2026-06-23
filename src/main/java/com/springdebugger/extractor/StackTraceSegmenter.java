package com.springdebugger.extractor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a noisy log buffer into individual error blocks so each can be diagnosed on its own.
 * An integration run (e.g. a Robot Framework suite hitting many endpoints) can throw several
 * distinct server-side errors into one log; the rest of the engine only ever pulled the single
 * deepest cause from the whole buffer, collapsing them to one.
 *
 * <p>A block boundary is a top-level exception printed at column zero (a fully-qualified class
 * ending in Exception/Error). "Caused by:" lines and "\tat ..." frames start with other text,
 * so they stay inside their block. If fewer than two boundaries are found the whole text is
 * returned unchanged, which preserves the existing single-error and banner-only behaviour
 * exactly.
 */
public final class StackTraceSegmenter {

    private static final Pattern BOUNDARY =
            Pattern.compile("(?m)^([\\w.$]+(?:Exception|Error))(?::|\\s|$)");

    private StackTraceSegmenter() {}

    public static List<String> segment(String text) {
        if (text == null || text.isBlank()) return List.of();

        Matcher m = BOUNDARY.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
        }
        if (starts.size() <= 1) {
            return List.of(text);
        }

        List<String> regions = new ArrayList<>(starts.size());
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = (i + 1 < starts.size()) ? starts.get(i + 1) : text.length();
            regions.add(text.substring(from, to));
        }
        return regions;
    }
}
