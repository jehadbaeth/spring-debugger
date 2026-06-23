package com.springdebugger.tap;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the pure fragment-merge used when walking the test tree. The regression this guards:
 * the root proxy's message is generic ("Failed to load ApplicationContext"), while the real
 * cause lives on a child's stacktrace — both must end up in the text handed to the extractor.
 */
class TestConsoleTapTest {

    @Test
    void mergesRootMessageAndChildStacktraceDeduplicated() {
        String rootMsg = "Failed to load ApplicationContext";
        String childTrace = "java.lang.IllegalStateException: Failed to load ApplicationContext\n"
                + "Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: "
                + "No qualifying bean of type 'com.example.Foo' available";

        String merged = TestConsoleTap.buildFailureText(
                Arrays.asList(rootMsg, null, childTrace, "", childTrace));

        // The deep cause from the child trace is present (this is what 1.10-vs-2.1 hinges on).
        assertThat(merged).contains("NoSuchBeanDefinitionException").contains("bean of type");
        // De-duplicated: the repeated child trace appears once.
        assertThat(merged.split("NoSuchBeanDefinitionException", -1).length - 1).isEqualTo(1);
    }

    @Test
    void blankWhenNoFragments() {
        assertThat(TestConsoleTap.buildFailureText(Arrays.asList(null, "", "   "))).isEmpty();
    }
}
