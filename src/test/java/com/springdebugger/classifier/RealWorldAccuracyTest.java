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
            "1.13",
            "MATCH",
            "yawintutor.com — Spring Boot 2.3, constructor-injection circular dependency. Resolves to 1.13 (BeanCurrentlyInCreationException, the deepest nested cause) once the extractor parses inline 'nested exception is' chains; same correct circular-dependency diagnosis as SOL-008"
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
            "2.3",
            "MATCH",
            "jvt.me — Spring Boot 2.x, NoSuchBeanDefinitionException in @SpringBootTest. Deepest Caused by is UnsatisfiedDependencyException (the missing bean is inline via 'nested exception is'), so rule 2.3 fires with a more specific diagnosis than the 1.10 catch-all now that 1.10 is last"
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
            "4.14",
            "MATCH",
            "GitHub spring-projects/spring-boot#34394 — Spring Boot 3.0.2, Redis connection factory missing; now covered by rule 4.14 added after this gap was found"
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
            "10.1",
            "MATCH",
            "GitHub spring-projects/spring-boot#38617 — Spring Boot 3.2.0 upgrade, NoSuchMethodError in test context; rule 10.1 now covers TEST phase and wins over the 1.10 catch-all (moved to end of catalog)"
        ),
        // ── v0.3.0 corpus expansion: 13 logs from public sources ──────────────────
        new TestCase(
            "NEW-001", "real-world-logs/NEW-001-jackson-infinite-recursion.log",
            Phase.RUNTIME, "7.2", "MATCH",
            "keenformatics.com — Jackson Infinite recursion (StackOverflowError) on a bidirectional JPA relationship"
        ),
        new TestCase(
            "NEW-002", "real-world-logs/NEW-002-jackson-no-default-constructor.log",
            Phase.RUNTIME, "7.4", "MATCH",
            "medium.com/@ranjani.harish12 — Jackson InvalidDefinitionException, cannot construct PageImpl (no default constructor); inline 'nested exception is' format, matched via the extractor's nested-cause fallback"
        ),
        new TestCase(
            "NEW-003", "real-world-logs/NEW-003-data-integrity-violation.log",
            Phase.RUNTIME, "4.13", "MATCH",
            "facingissuesonit.com — H2 unique constraint DataIntegrityViolationException; deepest cause is the JDBC driver exception, so rule 4.13 now keys on the wrapper text rather than the deepest class"
        ),
        new TestCase(
            "NEW-004", "real-world-logs/NEW-004-no-handler-found-404.log",
            Phase.RUNTIME, "5.1", "MATCH",
            "coderanch.com / Baeldung — NoHandlerFoundException (404); top-level exception with no Caused by, matched via the extractor's top-level fallback. (404 stack pattern reconstructed from multiple sources; see analysis doc)"
        ),
        new TestCase(
            "NEW-005", "real-world-logs/NEW-005-method-argument-not-valid.log",
            Phase.RUNTIME, "5.5", "MATCH",
            "salithachathuranga94.medium.com — MethodArgumentNotValidException bean validation, logged inside 'Resolved [...]'; rule 5.5 now keys on the wrapper text"
        ),
        new TestCase(
            "NEW-006", "real-world-logs/NEW-006-failed-to-bind-properties.log",
            Phase.STARTUP, "3.4", "MATCH",
            "onecompiler.com — Failed to bind server.port to Integer; failure analysis banner match"
        ),
        new TestCase(
            "NEW-007", "real-world-logs/NEW-007-yaml-scanner-exception.log",
            Phase.STARTUP, "3.5", "MATCH",
            "GitHub spring-projects/spring-boot#8438 — SnakeYAML ScannerException from an unresolved @placeholder@ in application.yml; top-level exception fallback"
        ),
        new TestCase(
            "NEW-008", "real-world-logs/NEW-008-no-embedded-db-driver.log",
            Phase.STARTUP, "4.1", "MATCH",
            "digitalocean.com — Cannot determine embedded database driver class for database type NONE; now wins over the generic 1.3 wrapper after 1.3 was moved late in the catalog"
        ),
        new TestCase(
            "NEW-009", "real-world-logs/NEW-009-hikaricp-pool-exhausted.log",
            Phase.RUNTIME, "4.4", "MATCH",
            "GitHub brettwooldridge/HikariCP#1798 — SQLTransientConnectionException, HikariPool connection not available, request timed out"
        ),
        new TestCase(
            "NEW-010", "real-world-logs/NEW-010-port-already-in-use.log",
            Phase.STARTUP, "1.8", "MATCH",
            "javaguides.net — PortInUseException, port 8080 already in use; failure analysis banner match"
        ),
        new TestCase(
            "NEW-011", "real-world-logs/NEW-011-generated-security-password.log",
            Phase.STARTUP, "6.2", "MATCH",
            "yawintutor.com — Spring Security 'Using generated security password' default configuration warning"
        ),
        new TestCase(
            "NEW-012", "real-world-logs/NEW-012-mapstruct-unmapped-target.log",
            Phase.COMPILE, "13.1", "MATCH",
            "GitHub mapstruct/mapstruct#2301 — MapStruct 'Unmapped target property' compile error"
        ),
        new TestCase(
            "NEW-013", "real-world-logs/NEW-013-mapstruct-no-such-bean.log",
            Phase.STARTUP, "13.4", "MATCH",
            "groups.google.com/g/mapstruct-users — NoSuchBeanDefinitionException for a @Mapper that lacks componentModel = \"spring\""
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
