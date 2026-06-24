package com.springdebugger.extractor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LogFilePropertyFinderTest {

    @Test
    void discoverAllResolvesOneLogPerServiceAgainstItsModule(@TempDir Path root) throws Exception {
        // Two services, each committing logging.file.name in its own application.properties.
        Path aRes = root.resolve("svc-a/src/main/resources");
        Files.createDirectories(aRes);
        Files.writeString(aRes.resolve("application.properties"), "logging.file.name=build/a.log\n");
        Path bRes = root.resolve("svc-b/src/main/resources");
        Files.createDirectories(bRes);
        Files.writeString(bRes.resolve("application.properties"), "logging.file.name=build/b.log\n");
        // A console-only service: no logging.file.name, must not be included.
        Path cRes = root.resolve("svc-c/src/main/resources");
        Files.createDirectories(cRes);
        Files.writeString(cRes.resolve("application.properties"), "server.port=8083\n");

        List<File> found = LogFilePropertyFinder.discoverAll(root.toFile());
        List<String> paths = found.stream()
                .map(f -> f.getPath().replace(root.toFile().getPath(), "").replace(File.separatorChar, '/'))
                .collect(Collectors.toList());

        assertThat(paths).containsExactlyInAnyOrder("/svc-a/build/a.log", "/svc-b/build/b.log");
    }

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
