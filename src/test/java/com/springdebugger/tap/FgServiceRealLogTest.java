package com.springdebugger.tap;

import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.rule.RuleCatalog;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A genuine multi-module Gradle bootRun (two services started together, no infra) reported by a
 * user. It carries three independent problems buried in two full CONDITIONS EVALUATION REPORTs and
 * repeated Kafka WARN spam:
 *   - a PostgreSQL connection refused (Hibernate keeps retrying)   -> rule 4.15
 *   - a missing bean 'CloseApproach' (APPLICATION FAILED TO START) -> rule 2.1
 *   - the Kafka broker-down WARN spam from four consumers          -> rule 14.1
 *
 * This locks two things the real log exposed: the conditions-report noise must not produce phantom
 * cards (we assert the EXACT set), and the four consumers' repeated connection failures must fold
 * into a single Kafka card (the user's "do you handle repeating retry errors" question).
 */
class FgServiceRealLogTest {

    private static String log() {
        try (InputStream is = FgServiceRealLogTest.class
                .getResourceAsStream("/real-world-logs/FG-SERVICE-gradle-multimodule-bootrun.log")) {
            return new String(Objects.requireNonNull(is).readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void surfacesAllThreeDistinctProblemsAndDeduplicatesTheSpam() {
        List<DiagnosisCard> cards = new ConsoleDiagnoser(RuleCatalog.load()).diagnoseAll(log(), null);
        List<String> rules = cards.stream().map(DiagnosisCard::getRuleId).collect(Collectors.toList());
        // Exactly three: no phantom cards from the thousands of conditions-report lines, and the
        // repeated Kafka spam collapsed to one 14.1 card.
        assertThat(rules).containsExactlyInAnyOrder("4.15", "2.1", "14.1");
        assertThat(rules.stream().filter("14.1"::equals).count()).isEqualTo(1);
    }

    @Test
    void triggerGateRecognisesThisOutput() {
        assertThat(RunConsoleTap.containsErrorSignature(log())).isTrue();
    }
}
