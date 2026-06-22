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
    void anyLineContainsIsCaseInsensitive() {
        String log = "Error creating bean with name 'userService'";
        RawSignal signal = extractor.extract(log, Phase.STARTUP);
        assertThat(signal.anyLineContains("error creating")).isTrue();
        assertThat(signal.anyLineContains("MISSING_STRING")).isFalse();
    }
}
