package com.springdebugger.extractor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogFilePropertyFinderTest {

    @Test
    void readsLoggingFileNameFromProperties() {
        String props = """
            spring.application.name=fg-analysis
            logging.file.name=build/logs/fg-analysis.log
            server.port=9351
            """;
        assertThat(LogFilePropertyFinder.fromProperties(props)).isEqualTo("build/logs/fg-analysis.log");
    }

    @Test
    void readsNestedLoggingFileNameFromYaml() {
        String yaml = """
            logging:
              file:
                name: build/logs/app.log
            server:
              port: 8080
            """;
        assertThat(LogFilePropertyFinder.fromYaml(yaml)).isEqualTo("build/logs/app.log");
    }

    @Test
    void readsFlattenedYamlKey() {
        assertThat(LogFilePropertyFinder.fromYaml("logging.file.name: out/app.log"))
                .isEqualTo("out/app.log");
    }

    @Test
    void returnsNullWhenConsoleOnly() {
        // The reported services log to console only: no logging.file.name set.
        assertThat(LogFilePropertyFinder.fromProperties("server.port=9351\nspring.profiles.active=local")).isNull();
        assertThat(LogFilePropertyFinder.fromYaml("server:\n  port: 8080")).isNull();
    }
}
