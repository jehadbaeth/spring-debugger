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
 * Locks the end-to-end coverage of the samples/testbed app. The fixture is the real combined
 * output of one `./gradlew test` run of the testbed (context-load failures + runtime web
 * errors). Running it through the multi-error path must surface every rule the testbed targets,
 * de-duplicated. If a rule signal drifts away from real Spring output, this test fails.
 */
class TestbedCoverageTest {

    @Test
    void engineExtractsEveryRuleTheTestbedTriggers() {
        String log;
        try (InputStream is = getClass().getResourceAsStream("/real-world-logs/TESTBED-combined-run.log")) {
            log = new String(Objects.requireNonNull(is).readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<String> rules = new ConsoleDiagnoser(RuleCatalog.load()).diagnoseAll(log, null).stream()
                .map(DiagnosisCard::getRuleId).collect(Collectors.toList());

        assertThat(rules).contains("1.13", "2.1", "2.2", "3.1", "5.5", "5.8", "5.13", "7.2");
    }
}
