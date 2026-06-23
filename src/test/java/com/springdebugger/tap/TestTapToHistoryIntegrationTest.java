package com.springdebugger.tap;

import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
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
public class TestTapToHistoryIntegrationTest extends LightJavaCodeInsightFixtureTestCase {

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

    public void testMissingComponentGivesSpecificAnnotationGuidance() {
        SpringDebuggerSettings settings = SpringDebuggerSettings.getInstance();
        settings.setShowNotificationBalloon(false);
        settings.setFocusToolWindowOnError(false);

        // The missing class actually exists in the project (no stereotype) so PSI can resolve
        // it and the enricher can name it, its file, and the best annotation.
        myFixture.addClass("package org.springframework.boot.autoconfigure; "
                + "public @interface SpringBootApplication {}");
        myFixture.addClass("package com.example.fg; "
                + "@org.springframework.boot.autoconfigure.SpringBootApplication public class App {}");
        myFixture.addClass("package com.example.fg; public class FgClassifier {}");

        DiagnosisHistoryService history = DiagnosisHistoryService.getInstance(getProject());
        history.clear();

        String trace = "java.lang.IllegalStateException: Failed to load ApplicationContext\n"
                + "Caused by: org.springframework.beans.factory.UnsatisfiedDependencyException: "
                + "Error creating bean with name 'fgIdentificationService'\n"
                + "Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: "
                + "No qualifying bean of type 'com.example.fg.FgClassifier' available: "
                + "expected at least 1 bean which qualifies as autowire candidate";

        TestConsoleTap tap = new TestConsoleTap(getProject(), RuleCatalog.load());
        SMTestProxy.SMRootTestProxy root = new SMTestProxy.SMRootTestProxy();
        SMTestProxy suite = new SMTestProxy("FgIdentificationServiceApplicationTest", true, null);
        root.addChild(suite);
        suite.setTestFailed("Failed to load ApplicationContext", trace, true);

        tap.onTestingFinished(root);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        DiagnosisCard card = history.getMostRecent();
        assertNotNull(card);
        assertEquals("2.1", card.getRuleId());
        // Specific guidance, not the generic template:
        assertTrue("diagnosis should name the class, was: " + card.getDiagnosisSentence(),
                card.getDiagnosisSentence().contains("com.example.fg.FgClassifier"));
        assertTrue("diagnosis should explain the missing stereotype, was: " + card.getDiagnosisSentence(),
                card.getDiagnosisSentence().contains("no Spring stereotype"));
        assertTrue("diagnosis should name the consuming bean, was: " + card.getDiagnosisSentence(),
                card.getDiagnosisSentence().contains("fgIdentificationService"));
        assertTrue("fix should recommend a concrete annotation, was: " + card.getFixSentence(),
                card.getFixSentence().contains("@Component"));
        assertTrue("fix should point at the file, was: " + card.getFixSentence(),
                card.getFixSentence().contains("FgClassifier.java"));
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
