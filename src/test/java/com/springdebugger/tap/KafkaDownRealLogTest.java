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
 * A genuine "Kafka broker down" log (no infra running, repeated WARN connection-failure spam,
 * no exception/ERROR line). Reported by a user whose bootRun produced nothing. Both the trigger
 * gate and the rule must handle it.
 */
class KafkaDownRealLogTest {

    private static String log() {
        try (InputStream is = KafkaDownRealLogTest.class
                .getResourceAsStream("/real-world-logs/SOL-kafka-down-warn-spam.log")) {
            return new String(Objects.requireNonNull(is).readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void warnConnectionSpamTriggersAnalysis() {
        // The taps must decide this WARN-only output is worth analysing.
        assertThat(RunConsoleTap.containsErrorSignature(log())).isTrue();
    }

    @Test
    void diagnosesBrokerUnreachableAndDeduplicatesTheSpam() {
        List<DiagnosisCard> cards = new ConsoleDiagnoser(RuleCatalog.load()).diagnoseAll(log(), null);
        List<String> rules = cards.stream().map(DiagnosisCard::getRuleId).collect(Collectors.toList());
        assertThat(rules).containsExactly("14.1"); // one card despite the repeated spam
    }
}
