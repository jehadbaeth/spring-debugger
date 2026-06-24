package com.springdebugger.extractor;

import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.rule.RuleCatalog;
import com.springdebugger.tap.ConsoleDiagnoser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An appending log file accumulates multiple bootRun runs (Spring Boot does not truncate between
 * them). The tailer must diagnose only the latest run, so a stale error from a previous run is not
 * re-surfaced.
 */
class LogRunBoundaryTest {

    private static final String TWO_RUNS = """
        2026-06-24T10:00:00 INFO 100 --- [main] c.e.App : Starting App using Java 21 with PID 100
        2026-06-24T10:00:01 ERROR 100 --- [main] o.h.e.jdbc : Connection to localhost:5432 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.
        2026-06-24T10:00:02 INFO 100 --- [main] c.e.App : Started App
        2026-06-24T10:05:00 INFO 200 --- [main] c.e.App : Starting App using Java 21 with PID 200
        2026-06-24T10:05:01 INFO 200 --- [main] c.e.App : Started App in 3s
        """;

    @Test
    void detectsRunStart() {
        assertThat(LogRunBoundary.isRunStart(
                "2026 INFO --- c.e.App : Starting App using Java 21 with PID 100")).isTrue();
        assertThat(LogRunBoundary.isRunStart("2026 INFO --- c.e.App : Started App")).isFalse();
    }

    @Test
    void lastRunSliceDropsTheEarlierRun() {
        String slice = LogRunBoundary.lastRunSlice(TWO_RUNS);
        assertThat(slice).contains("with PID 200", "Started App in 3s");
        assertThat(slice).doesNotContain("PID 100", "Connection to localhost:5432 refused");
    }

    @Test
    void staleErrorFromAPreviousRunIsNotDiagnosed() {
        // The DB error is in run #1; run #2 (the current one) started clean. Diagnosing the latest
        // slice must yield nothing, not a stale 4.15.
        String slice = LogRunBoundary.lastRunSlice(TWO_RUNS);
        List<DiagnosisCard> cards = new ConsoleDiagnoser(RuleCatalog.load()).diagnoseAll(slice, null);
        List<String> rules = cards.stream().map(DiagnosisCard::getRuleId).collect(Collectors.toList());
        assertThat(rules).doesNotContain("4.15");
    }

    @Test
    void noMarkerReturnsWholeText() {
        String plain = "just some output\nwith no spring boot start line\n";
        assertThat(LogRunBoundary.lastRunSlice(plain)).isEqualTo(plain);
    }
}
