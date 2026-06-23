package com.springdebugger.terminal;

/**
 * Tracks the text already seen from a terminal so each poll only yields the newly-appended
 * output. A terminal has no output events, so the monitor polls its text buffer; this keeps it
 * from re-analysing the whole scrollback every tick. Pure and unit-tested.
 *
 * <p>When the buffer scrolls and the previous text is no longer a prefix of the current text
 * (old lines dropped off the top), it re-baselines and returns the whole current text once.
 */
public final class TerminalTextDiffer {

    private String last = "";

    /** Returns the text appended since the previous call (or the full text after a scroll/reset). */
    public synchronized String newText(String current) {
        if (current == null || current.isEmpty()) return "";
        if (current.length() >= last.length() && current.startsWith(last)) {
            String delta = current.substring(last.length());
            last = current;
            return delta;
        }
        last = current;
        return current;
    }

    public synchronized void reset() {
        last = "";
    }
}
