package com.springdebugger.tap;

import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the test-tree traversal AND the fragment-merge. The traversal tests build a real
 * SM test tree and put the deep cause on different nodes (suite, leaf, root), because in
 * practice a context-load failure is recorded on the class-level suite node and the root
 * proxy can report isDefect() == false — the original bug was gating on the root and reading
 * only its message.
 */
class TestConsoleTapTest {

    private static final String DEEP_CAUSE =
            "java.lang.IllegalStateException: Failed to load ApplicationContext\n"
            + "Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: "
            + "No qualifying bean of type 'com.example.Foo' available";

    @Test
    void collectsDeepCauseWhenRecordedOnSuiteNode() {
        // The discriminating case: failure on the class-level suite node, root not defect.
        SMTestProxy.SMRootTestProxy root = new SMTestProxy.SMRootTestProxy();
        SMTestProxy suite = new SMTestProxy("FgIdentificationServiceApplicationTest", true, null);
        SMTestProxy leaf = new SMTestProxy("contextLoads", false, null);
        root.addChild(suite);
        suite.addChild(leaf);
        suite.setTestFailed("Failed to load ApplicationContext", DEEP_CAUSE, true);

        assertThat(root.isDefect())
                .as("guard must not rely on the root being defect")
                .isFalse();
        String text = TestConsoleTap.collectFailureText(root);
        assertThat(text).contains("NoSuchBeanDefinitionException").contains("bean of type");
    }

    @Test
    void collectsDeepCauseWhenRecordedOnLeafNode() {
        SMTestProxy.SMRootTestProxy root = new SMTestProxy.SMRootTestProxy();
        SMTestProxy suite = new SMTestProxy("SomeTest", true, null);
        SMTestProxy leaf = new SMTestProxy("itFails", false, null);
        root.addChild(suite);
        suite.addChild(leaf);
        leaf.setTestFailed("boom", DEEP_CAUSE, true);

        assertThat(TestConsoleTap.collectFailureText(root)).contains("NoSuchBeanDefinitionException");
    }

    @Test
    void emptyWhenNothingFailed() {
        SMTestProxy.SMRootTestProxy root = new SMTestProxy.SMRootTestProxy();
        SMTestProxy suite = new SMTestProxy("CleanTest", true, null);
        root.addChild(suite);

        assertThat(TestConsoleTap.collectFailureText(root)).isBlank();
    }

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
