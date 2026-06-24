package com.springdebugger.extractor;

import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.rule.RuleCatalog;
import com.springdebugger.tap.ConsoleDiagnoser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The terminal-agnostic test path: a Gradle/Surefire results XML from `./gradlew test` (run in any
 * terminal) must yield the same diagnosis the IDE test runner would. Proves capture works without
 * any terminal hook.
 */
class TestResultsParserTest {

    // A real-shaped Gradle test-results file: a Spring context-load failure with a missing-bean
    // cause embedded as the <failure> body, exactly as Gradle writes it.
    private static final String XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <testsuite name="com.example.OrderServiceTest" tests="1" skipped="0" failures="1" errors="0" time="1.2">
          <testcase name="loadsContext" classname="com.example.OrderServiceTest" time="1.1">
            <failure message="java.lang.IllegalStateException: Failed to load ApplicationContext" type="java.lang.IllegalStateException">
        java.lang.IllegalStateException: Failed to load ApplicationContext for [WebMergedContextConfiguration@1]
        Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.PricingClient' available: expected at least 1 bean which qualifies as autowire candidate
        	at org.springframework.beans.factory.support.DefaultListableBeanFactory.raiseNoMatchingBeanFound(DefaultListableBeanFactory.java:2144)
            </failure>
          </testcase>
        </testsuite>
        """;

    @Test
    void extractsFailureBodyFromResultsXml() {
        List<String> failures = TestResultsParser.failureTexts(XML);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).contains("NoSuchBeanDefinitionException", "PricingClient");
    }

    @Test
    void extractedFailureFeedsTheEngineToAConcreteDiagnosis() {
        String failure = TestResultsParser.failureTexts(XML).get(0);
        List<DiagnosisCard> cards = new ConsoleDiagnoser(RuleCatalog.load()).diagnoseAll(failure, null);
        List<String> rules = cards.stream().map(DiagnosisCard::getRuleId).collect(Collectors.toList());
        // The missing-bean rule fires from the test-results XML, no terminal involved.
        assertThat(rules).contains("2.1");
    }

    @Test
    void cleanSuiteYieldsNothing() {
        String passing = "<testsuite name=\"x\" tests=\"3\" failures=\"0\" errors=\"0\"><testcase name=\"a\"/></testsuite>";
        assertThat(TestResultsParser.hasFailures(passing)).isFalse();
        assertThat(TestResultsParser.failureTexts(passing)).isEmpty();
    }
}
