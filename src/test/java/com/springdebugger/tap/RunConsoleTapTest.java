package com.springdebugger.tap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the pure error-signature gate that triggers debounced analysis. */
class RunConsoleTapTest {

    @Test
    void recognisesStartupAndRuntimeFailures() {
        assertThat(RunConsoleTap.containsErrorSignature("...APPLICATION FAILED TO START...")).isTrue();
        assertThat(RunConsoleTap.containsErrorSignature("Exception in thread \"main\" ...")).isTrue();
        assertThat(RunConsoleTap.containsErrorSignature("Caused by: java.lang.NullPointerException")).isTrue();
        assertThat(RunConsoleTap.containsErrorSignature(
                "2024 ERROR o.s.boot ... NestedServletException: ... NullPointerException")).isTrue();
    }

    @Test
    void ignoresOrdinaryOutput() {
        assertThat(RunConsoleTap.containsErrorSignature("Started DemoApplication in 3.4 seconds")).isFalse();
        assertThat(RunConsoleTap.containsErrorSignature("Tomcat started on port 8080")).isFalse();
        assertThat(RunConsoleTap.containsErrorSignature(null)).isFalse();
    }
}
