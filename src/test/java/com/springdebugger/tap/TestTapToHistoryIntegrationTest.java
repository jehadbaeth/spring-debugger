package com.springdebugger.tap;

import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.rule.RuleCatalog;
import com.springdebugger.service.DiagnosisHistoryService;
import com.springdebugger.settings.SpringDebuggerSettings;

/**
 * End-to-end, in-process verification of the test tap: a real SM test tree with a
 * context-load failure goes through TestConsoleTap -> pipeline -> DiagnosisCardPanel.show ->
 * DiagnosisHistoryService, and the renderable model (the history the tool window binds to)
 * ends up holding the correct, specific diagnosis. This is the headless stand-in for the
 * reported GUI scenario: a @SpringBootTest missing a @Component must yield rule 2.1, not the
 * 1.10 umbrella, and the card must actually reach the panel's data source.
 */
public class TestTapToHistoryIntegrationTest extends BasePlatformTestCase {

    private static final String MISSING_BEAN_TRACE =
            "java.lang.IllegalStateException: Failed to load ApplicationContext\n"
            + "Caused by: org.springframework.beans.factory.UnsatisfiedDependencyException: "
            + "Error creating bean with name 'fgService'\n"
            + "Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: "
            + "No qualifying bean of type 'com.example.FgClassifier' available: "
            + "expected at least 1 bean which qualifies as autowire candidate";

    public void testMissingComponentSurfacesRule21InHistory() {
        // Isolate the data path from the balloon/tool-window so the assertion is about the card.
        SpringDebuggerSettings settings = SpringDebuggerSettings.getInstance();
        settings.setShowNotificationBalloon(false);
        settings.setFocusToolWindowOnError(false);

        DiagnosisHistoryService history = DiagnosisHistoryService.getInstance(getProject());
        history.clear();

        TestConsoleTap tap = new TestConsoleTap(getProject(), RuleCatalog.load());

        SMTestProxy.SMRootTestProxy root = new SMTestProxy.SMRootTestProxy();
        SMTestProxy suite = new SMTestProxy("FgIdentificationServiceApplicationTest", true, null);
        SMTestProxy leaf = new SMTestProxy("contextLoads", false, null);
        root.addChild(suite);
        suite.addChild(leaf);
        // Failure recorded on the suite node (root.isDefect() can be false): the bug case.
        suite.setTestFailed("Failed to load ApplicationContext", MISSING_BEAN_TRACE, true);

        tap.onTestingFinished(root);

        // DiagnosisCardPanel.show marshals to the EDT via invokeLater; pump the queue.
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        DiagnosisCard recent = history.getMostRecent();
        assertNotNull("a diagnosis card should have reached the history model", recent);
        assertEquals("missing @Component must give the specific bean diagnosis, not the umbrella",
                "2.1", recent.getRuleId());
    }

    public void testCleanRunProducesNoCard() {
        DiagnosisHistoryService history = DiagnosisHistoryService.getInstance(getProject());
        history.clear();

        TestConsoleTap tap = new TestConsoleTap(getProject(), RuleCatalog.load());
        SMTestProxy.SMRootTestProxy root = new SMTestProxy.SMRootTestProxy();
        SMTestProxy suite = new SMTestProxy("CleanTest", true, null);
        root.addChild(suite);

        tap.onTestingFinished(root);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertNull("a passing run must not produce a card", history.getMostRecent());
    }
}
