package com.springdebugger.tap;

import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.rule.RuleCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-error diagnosis over a noisy integration-test log, driven with a null project (no IDE).
 */
class ConsoleDiagnoserTest {

    private static ConsoleDiagnoser diagnoser;

    @BeforeAll
    static void setup() {
        diagnoser = new ConsoleDiagnoser(RuleCatalog.load());
    }

    @Test
    void surfacesEveryDistinctErrorFromANoisyLog() {
        String log = """
                2024-01-01 10:00:00 ERROR c.e.web : request failed
                org.springframework.web.servlet.NoHandlerFoundException: No handler found for GET /api/missing
                \tat org.springframework.web.servlet.DispatcherServlet.noHandlerFound(DispatcherServlet.java:1)
                2024-01-01 10:00:05 ERROR c.e.web : write failed
                org.springframework.dao.DataIntegrityViolationException: could not execute statement; constraint [uq_email]
                \tat org.springframework.orm.jpa.HibernateJpaDialect.convert(HibernateJpaDialect.java:1)
                2024-01-01 10:00:09 ERROR c.e.kafka : send failed
                org.apache.kafka.common.errors.TimeoutException: Failed to update metadata after 60000 ms.
                """;
        List<String> ruleIds = diagnoser.diagnoseAll(log, null).stream()
                .map(DiagnosisCard::getRuleId).collect(Collectors.toList());

        assertThat(ruleIds).containsExactlyInAnyOrder("5.1", "4.13", "14.1");
    }

    @Test
    void deduplicatesTheSameErrorHitManyTimes() {
        String oneError = "org.springframework.web.servlet.NoHandlerFoundException: No handler found for GET /api/x\n"
                + "\tat org.springframework.web.servlet.DispatcherServlet.noHandlerFound(DispatcherServlet.java:1)\n";
        String log = oneError + oneError + oneError;

        List<DiagnosisCard> cards = diagnoser.diagnoseAll(log, null);
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getRuleId()).isEqualTo("5.1");
    }

    @Test
    void cleanLogProducesNothing() {
        assertThat(diagnoser.diagnoseAll("Started DemoApplication in 3.1 seconds\n", null)).isEmpty();
    }
}
