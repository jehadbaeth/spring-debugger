package com.springdebugger.classifier;

import com.springdebugger.extractor.LogExtractor;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestReporter;
import com.springdebugger.rule.RuleCatalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Accuracy test against real-world Spring Boot error logs collected from
 * public sources (GitHub Issues, blog posts). Each test case documents:
 *   - Source: where the log was found
 *   - Expected rule: which rule should match (or null if no match expected)
 *   - Expected verdict: MATCH or NO_MATCH (false negatives are failures)
 *
 * A NO_MATCH expectation means the classifier cannot handle this log format
 * and the limitation is acknowledged in ACCURACY-ANALYSIS-v0.1.0.md.
 */
class RealWorldAccuracyTest {

    record TestCase(
            String id,
            String logFile,
            Phase phase,
            String expectedRuleId,
            String verdict,
            String source
    ) {
        boolean expectsMatch() { return "MATCH".equals(verdict); }
    }

    private static final List<TestCase> TEST_CASES = List.of(
        // ── Original 5 cases ──────────────────────────────────────────────────────
        new TestCase(
            "RW-001",
            "real-world-logs/RW-001-circular-dependency.log",
            Phase.STARTUP,
            "2.7",
            "MATCH",
            "yawintutor.com — Spring Boot 2.3, constructor-injection circular dependency"
        ),
        new TestCase(
            "RW-002",
            "real-world-logs/RW-002-datasource-not-configured.log",
            Phase.STARTUP,
            "4.2",
            "MATCH",
            "yawintutor.com — Spring Boot 3, missing DataSource URL"
        ),
        new TestCase(
            "RW-003",
            "real-world-logs/RW-003-no-password-encoder.log",
            Phase.RUNTIME,
            "6.5",
            "MATCH",
            "yawintutor.com — Spring Boot 2.2, Spring Security DelegatingPasswordEncoder"
        ),
        new TestCase(
            "RW-004",
            "real-world-logs/RW-004-nosuchbeandef-legacy-format.log",
            Phase.STARTUP,
            null,
            "NO_MATCH",
            "GitHub spring-projects/spring-boot#4519 — Spring Boot 1.3, legacy 'nested exception is' format without Caused by lines and without failure analysis banner"
        ),
        new TestCase(
            "RW-005",
            "real-world-logs/RW-005-nosuchbeandef-modern-partial.log",
            Phase.STARTUP,
            null,
            "NO_MATCH",
            "GitHub apache/shardingsphere#7933 — Spring Boot 2.3.1, partial log (exception only, no failure analysis banner; Caused by message uses 'expected at least 1 bean' not 'required a bean of type')"
        ),
        // ── SOLogFetcher collection (GitHub Issues, blog sources) ─────────────────
        new TestCase(
            "SOL-001",
            "real-world-logs/SOL-001-nosuchbean-test-context.log",
            Phase.TEST,
            "1.10",
            "MATCH",
            "jvt.me — Spring Boot 2.x, NoSuchBeanDefinitionException in @SpringBootTest; generic 'Failed to load ApplicationContext' fires as catch-all"
        ),
        new TestCase(
            "SOL-002",
            "real-world-logs/SOL-002-nosuchbean-with-banner.log",
            Phase.STARTUP,
            "2.1",
            "MATCH",
            "ggorantala.dev — Spring Boot 2.x, NoSuchBeanDefinitionException with failure analysis banner containing 'required a bean of type'"
        ),
        new TestCase(
            "SOL-003",
            "real-world-logs/SOL-003-redis-chain-no-match.log",
            Phase.STARTUP,
            null,
            "NO_MATCH",
            "GitHub spring-projects/spring-boot#34394 — Spring Boot 3.0.2, Redis connection factory missing; deepest Caused by is IllegalStateException with technology-specific message not covered by any rule"
        ),
        new TestCase(
            "SOL-004",
            "real-world-logs/SOL-004-port-in-use-test-no-match.log",
            Phase.TEST,
            "1.10",
            "MATCH",
            "GitHub spring-projects/spring-boot#19382 — Spring Boot 2.x, PortInUseException in @SpringBootTest; 'Failed to load ApplicationContext' generic catch-all fires correctly"
        ),
        new TestCase(
            "SOL-005",
            "real-world-logs/SOL-005-unresolved-placeholder.log",
            Phase.STARTUP,
            "3.1",
            "MATCH",
            "yawintutor.com — Spring Boot 2.2.4, @Value placeholder 'message' not defined in any property source"
        ),
        new TestCase(
            "SOL-006",
            "real-world-logs/SOL-006-datasource-github-15828.log",
            Phase.STARTUP,
            "4.2",
            "MATCH",
            "GitHub spring-projects/spring-boot#15828 — Spring Boot 2.0.2, DataSource URL not configured; second confirmation of 4.2 detection"
        ),
        new TestCase(
            "SOL-007",
            "real-world-logs/SOL-007-lazy-init-exception.log",
            Phase.RUNTIME,
            "4.8",
            "MATCH",
            "GitHub redisson/redisson#2798 — Spring Boot 2.1.1 / Hibernate 5.2, LazyInitializationException accessing proxy outside session"
        ),
        new TestCase(
            "SOL-008",
            "real-world-logs/SOL-008-circular-springboot26.log",
            Phase.STARTUP,
            "1.13",
            "MATCH",
            "ggorantala.dev — Spring Boot 2.6+, BeanCurrentlyInCreationException; rule 1.13 (Caused by line) wins over rule 2.7 (banner text) by catalog order"
        ),
        new TestCase(
            "SOL-009",
            "real-world-logs/SOL-009-ambiguous-mapping.log",
            Phase.STARTUP,
            "5.8",
            "MATCH",
            "Medium/@junjaboy — Spring Boot 3.2.0 / Spring WebMVC 6.1.1, two controller methods mapped to the same GET path"
        ),
        new TestCase(
            "SOL-010",
            "real-world-logs/SOL-010-nosuchmethod-test-no-match.log",
            Phase.TEST,
            "1.10",
            "MATCH",
            "GitHub spring-projects/spring-boot#38617 — Spring Boot 3.2.0 upgrade, NoSuchMethodError in test context; rule 10.1 (STARTUP/RUNTIME only) skipped by phase filter; 'Failed to load ApplicationContext' catch-all fires correctly"
        )
    );

    private static RuleBasedClassifier classifier;
    private static LogExtractor extractor;

    @BeforeAll
    static void setup() {
        classifier = new RuleBasedClassifier(RuleCatalog.load());
        extractor = new LogExtractor();
    }

    @TestFactory
    Stream<DynamicTest> realWorldLogClassification() {
        return TEST_CASES.stream().map(tc -> DynamicTest.dynamicTest(
            tc.id() + ": " + tc.source().split("—")[0].trim(),
            () -> runCase(tc)
        ));
    }

    private void runCase(TestCase tc) throws IOException {
        String log = loadLog(tc.logFile());
        var signal = extractor.extract(log, tc.phase());
        Optional<DiagnosisCard> card = classifier.classify(signal);

        if (tc.expectsMatch()) {
            assertThat(card)
                .as("[%s] Expected rule %s to match but classifier returned nothing. Source: %s",
                    tc.id(), tc.expectedRuleId(), tc.source())
                .isPresent();
            assertThat(card.get().getRuleId())
                .as("[%s] Expected rule %s but got rule %s. Source: %s",
                    tc.id(), tc.expectedRuleId(), card.map(DiagnosisCard::getRuleId).orElse("none"), tc.source())
                .isEqualTo(tc.expectedRuleId());
        } else {
            // Documented false negative — assert no match so that if a future rule
            // accidentally fires on this log we notice immediately.
            assertThat(card)
                .as("[%s] Expected NO match (documented limitation) but classifier fired rule %s. Source: %s",
                    tc.id(), card.map(DiagnosisCard::getRuleId).orElse(""), tc.source())
                .isEmpty();
        }
    }

    private String loadLog(String path) throws IOException {
        String resourcePath = "/" + path;
        try (InputStream is = RealWorldAccuracyTest.class.getResourceAsStream(resourcePath)) {
            Objects.requireNonNull(is, "Test log not found on classpath: " + resourcePath);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
