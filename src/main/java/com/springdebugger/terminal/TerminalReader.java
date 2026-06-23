package com.springdebugger.terminal;

import com.intellij.terminal.JBTerminalWidget;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;

/**
 * Reads the full visible + scrollback text of a terminal widget. Thin IDE/JediTerm adapter:
 * the buffer is locked while read so it is consistent. Returns the history lines followed by
 * the on-screen lines, newline-separated.
 */
public final class TerminalReader {

    private TerminalReader() {}

    public static String read(JBTerminalWidget widget) {
        TerminalTextBuffer buffer = widget.getTerminalTextBuffer();
        buffer.lock();
        try {
            StringBuilder sb = new StringBuilder();
            int history = buffer.getHistoryLinesCount();
            for (int i = 0; i < history; i++) {
                TerminalLine line = buffer.getHistoryBuffer().getLine(i);
                if (line != null) sb.append(line.getText()).append('\n');
            }
            int screen = buffer.getScreenLinesCount();
            for (int i = 0; i < screen; i++) {
                TerminalLine line = buffer.getLine(i);
                if (line != null) sb.append(line.getText()).append('\n');
            }
            return sb.toString();
        } finally {
            buffer.unlock();
        }
    }
}
