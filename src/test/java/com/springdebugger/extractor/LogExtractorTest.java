package com.springdebugger.extractor;

import com.springdebugger.model.Phase;
import com.springdebugger.model.RawSignal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogExtractorTest {

    private final LogExtractor extractor = new LogExtractor();

    @Test
    void extractsDeepestCausedByClass() {
        String log = """
                org.springframework.context.ApplicationContextException: Unable to start web server
                \tCaused by: org.springframework.beans.factory.BeanCreationException: Error creating bean 'foo'
                \t\tCaused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.Foo'
                """;

        RawSignal signal = extractor.extract(log, Phase.STARTUP);

        assertThat(signal.getDeepestCausedByClass())
                .contains("NoSuchBeanDefinitionException");
    }

    @Test
    void extractsBannerDescription() {
        String log = """
                ***************************
                APPLICATION FAILED TO START
                ***************************

                Description:

                Web server failed to start. Port 8080 was already in use.

                Action:

                Identify and stop the process that's listening on port 8080.
                """;

        RawSignal signal = extractor.extract(log, Phase.STARTUP);

        assertThat(signal.getBannerDescription()).contains("Port 8080 was already in use");
        assertThat(signal.getBannerAction()).contains("Identify and stop");
    }

    @Test
    void extractsFailingBeanName() {
        String log = "Error creating bean with name 'orderService': Unsatisfied dependency";

        RawSignal signal = extractor.extract(log, Phase.STARTUP);

        assertThat(signal.getFailingBeanName()).isEqualTo("orderService");
    }

    @Test
    void returnsNonNullSignalForEmptyInput() {
        RawSignal signal = extractor.extract("", Phase.RUNTIME);
        assertThat(signal).isNotNull();
        assertThat(signal.getPhase()).isEqualTo(Phase.RUNTIME);
        assertThat(signal.hasDeepestCause()).isFalse();
        assertThat(signal.hasBanner()).isFalse();
    }

    @Test
    void fallsBackToDeepestNestedExceptionWhenNoCausedBy() {
        // Single-line inline chain (no "Caused by:" lines): the DEEPEST nested wins.
        String log = "org.springframework.beans.factory.UnsatisfiedDependencyException: "
                + "Error creating bean with name 'department'; nested exception is "
                + "org.springframework.beans.factory.UnsatisfiedDependencyException: tiger; nested exception is "
                + "org.springframework.beans.factory.BeanCurrentlyInCreationException: "
                + "Requested bean is currently in creation";

        RawSignal signal = extractor.extract(log, Phase.STARTUP);

        assertThat(signal.getDeepestCausedByClass()).contains("BeanCurrentlyInCreationException");
    }

    @Test
    void causedByWinsOverNestedException() {
        // When both styles are present, the canonical "Caused by:" chain is authoritative.
        String log = """
                org.example.Wrapper: failed; nested exception is org.example.InlineCause: inline
                \tCaused by: org.example.RealRootException: the real root
                """;

        RawSignal signal = extractor.extract(log, Phase.RUNTIME);

        assertThat(signal.getDeepestCausedByClass()).contains("RealRootException");
    }

    @Test
    void fallsBackToTopLevelExceptionLine() {
        // No "Caused by:" and no "nested exception is": use the top-level exception line.
        String log = """
                org.yaml.snakeyaml.scanner.ScannerException: while scanning for the next token
                \tat org.yaml.snakeyaml.scanner.ScannerImpl.fetchMoreTokens(ScannerImpl.java:420)
                """;

        RawSignal signal = extractor.extract(log, Phase.STARTUP);

        assertThat(signal.getDeepestCausedByClass()).isEqualTo("org.yaml.snakeyaml.scanner.ScannerException");
    }

    @Test
    void anyLineContainsIsCaseInsensitive() {
        String log = "Error creating bean with name 'userService'";
        RawSignal signal = extractor.extract(log, Phase.STARTUP);
        assertThat(signal.anyLineContains("error creating")).isTrue();
        assertThat(signal.anyLineContains("MISSING_STRING")).isFalse();
    }
}
