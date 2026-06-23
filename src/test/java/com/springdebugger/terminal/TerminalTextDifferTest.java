package com.springdebugger.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalTextDifferTest {

    @Test
    void returnsOnlyAppendedTextBetweenPolls() {
        TerminalTextDiffer differ = new TerminalTextDiffer();
        assertThat(differ.newText("line1\n")).isEqualTo("line1\n");
        assertThat(differ.newText("line1\nline2\n")).isEqualTo("line2\n");
        assertThat(differ.newText("line1\nline2\n")).isEmpty();
    }

    @Test
    void rebaselinesWhenBufferScrolledAndPrefixNoLongerMatches() {
        TerminalTextDiffer differ = new TerminalTextDiffer();
        differ.newText("old1\nold2\n");
        // History scrolled: the previous text is no longer a prefix; return the whole current.
        String current = "old2\nnew3\n";
        assertThat(differ.newText(current)).isEqualTo(current);
    }

    @Test
    void emptyAndNullYieldNothing() {
        TerminalTextDiffer differ = new TerminalTextDiffer();
        assertThat(differ.newText(null)).isEmpty();
        assertThat(differ.newText("")).isEmpty();
    }

    @Test
    void resetClearsBaseline() {
        TerminalTextDiffer differ = new TerminalTextDiffer();
        differ.newText("abc");
        differ.reset();
        assertThat(differ.newText("abc")).isEqualTo("abc");
    }
}
