package com.springdebugger.tap;

import com.springdebugger.classifier.RuleBasedClassifier;
import com.springdebugger.extractor.LogExtractor;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.rule.RuleCatalog;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-world multi-error test driven by a log captured from an actual Spring Boot 3.2 app
 * (built and run in the scratchpad, then hit on several endpoints that throw). It proves the
 * multi-error path digs the actionable, rule-covered error out of genuine noise where the old
 * single-pass classifier found nothing.
 */
class RealWorldMultiErrorTest {

    private static String load(String name) {
        try (InputStream is = RealWorldMultiErrorTest.class.getResourceAsStream("/real-world-logs/" + name)) {
            Objects.requireNonNull(is, "missing fixture " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void diagnoseAllExtractsTheValidationErrorFromRealIntegrationNoise() {
        RuleCatalog catalog = RuleCatalog.load();
        String log = load("LIVE-002-integration-runtime-errors.log");

        // This log is a genuine capture: an app started, then /npe, /ise, /missing and a bad
        // POST were hit, so the console interleaves a NullPointerException, an
        // IllegalStateException, a 404 and a validation failure. Of these, only the validation
        // failure maps to a rule (5.5); the multi-error path digs it out of the real noise.
        List<String> rules = new ConsoleDiagnoser(catalog).diagnoseAll(log, null).stream()
                .map(DiagnosisCard::getRuleId).collect(Collectors.toList());
        assertThat(rules).contains("5.5");

        // Sanity: the single deepest-cause pass at STARTUP phase (the wrong phase for these
        // runtime errors) finds nothing, which is why phase-aware multi-error analysis matters.
        Optional<DiagnosisCard> startupPass =
                new RuleBasedClassifier(catalog).classify(new LogExtractor().extract(log, Phase.STARTUP));
        assertThat(startupPass).isEmpty();
    }
}
