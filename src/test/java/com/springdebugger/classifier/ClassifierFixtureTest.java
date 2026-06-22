package com.springdebugger.classifier;

import com.springdebugger.extractor.LogExtractor;
import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.rule.Rule;
import com.springdebugger.rule.RuleCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixture-driven classifier tests.
 * For each rule in the catalog that has status DONE and a fixture file:
 *   1. Load the fixture log from the classpath
 *   2. Extract a RawSignal
 *   3. Assert the classifier returns the expected ruleId
 *   4. Assert confidence is HIGH or MEDIUM (never NONE)
 *
 * Rules with status TODO are skipped. A rule must not be moved to DONE
 * without a matching fixture file.
 */
class ClassifierFixtureTest {

    private static RuleCatalog catalog;
    private static RuleBasedClassifier classifier;
    private static LogExtractor extractor;

    @BeforeAll
    static void setup() {
        catalog = RuleCatalog.load();
        classifier = new RuleBasedClassifier(catalog);
        extractor = new LogExtractor();
    }

    @Test
    void catalogLoadsSuccessfully() {
        assertThat(catalog.size()).isGreaterThan(0);
    }

    @Test
    void allDoneRulesHaveFixtureFiles() {
        List<Rule> doneWithoutFixture = catalog.all().stream()
                .filter(r -> "DONE".equals(r.getStatus()))
                .filter(r -> r.getFixture() == null || r.getFixture().isBlank()
                        || ClassifierFixtureTest.class.getResourceAsStream("/" + r.getFixture()) == null)
                .toList();

        assertThat(doneWithoutFixture)
                .as("Rules marked DONE must have an accessible fixture file")
                .isEmpty();
    }

    @TestFactory
    Stream<DynamicTest> fixtureMatchesExpectedRule() {
        return catalog.all().stream()
                .filter(r -> "DONE".equals(r.getStatus()))
                .filter(r -> r.getFixture() != null && !r.getFixture().isBlank())
                .map(rule -> DynamicTest.dynamicTest("Rule " + rule.getId() + " matches fixture", () -> {
                    String fixtureContent = loadFixture(rule.getFixture());
                    Phase phase = rule.getPhases() != null && !rule.getPhases().isEmpty()
                            ? rule.getPhases().get(0)
                            : Phase.STARTUP;

                    var signal = extractor.extract(fixtureContent, phase);
                    Optional<DiagnosisCard> card = classifier.classify(signal);

                    assertThat(card).as("Classifier should produce a result for rule " + rule.getId()).isPresent();
                    assertThat(card.get().getRuleId())
                            .as("Expected rule " + rule.getId() + " to match the fixture")
                            .isEqualTo(rule.getId());
                    assertThat(card.get().getConfidence())
                            .as("Confidence must be HIGH or MEDIUM, not NONE or LOW")
                            .isNotIn(Confidence.NONE, Confidence.LOW);
                }));
    }

    private String loadFixture(String fixturePath) throws IOException {
        String resourcePath = "/" + fixturePath;
        try (InputStream is = ClassifierFixtureTest.class.getResourceAsStream(resourcePath)) {
            Objects.requireNonNull(is, "Fixture not found: " + resourcePath);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
