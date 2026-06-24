package com.springdebugger.extractor;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestResultsLocatorTest {

    @Test
    void findsGradleAndMavenResultsAndPrunesSource(@TempDir Path root) throws Exception {
        // Gradle layout: <module>/build/test-results/test/*.xml
        Path gradle = root.resolve("app/build/test-results/test");
        Files.createDirectories(gradle);
        Files.writeString(gradle.resolve("TEST-Foo.xml"), "<testsuite/>");
        // Maven layout: <module>/target/surefire-reports/*.xml
        Path maven = root.resolve("svc/target/surefire-reports");
        Files.createDirectories(maven);
        Files.writeString(maven.resolve("TEST-Bar.xml"), "<testsuite/>");
        // Noise that must be pruned or ignored.
        Path src = root.resolve("app/src/test/resources");
        Files.createDirectories(src);
        Files.writeString(src.resolve("not-a-result.xml"), "<x/>");

        List<String> names = TestResultsLocator.locate(root.toFile()).stream()
                .map(File::getName).collect(Collectors.toList());

        assertThat(names).containsExactlyInAnyOrder("TEST-Foo.xml", "TEST-Bar.xml");
    }
}
