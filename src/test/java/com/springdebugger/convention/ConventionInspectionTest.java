package com.springdebugger.convention;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.springdebugger.settings.SpringDebuggerSettings;

/**
 * Proves the inspection actually highlights: the squiggle lands on the violating method name and the
 * settings toggles suppress it. This is the headless equivalent of confirming the on-the-fly
 * trigger in a running IDE; the same buildVisitor path drives batch and commit-time runs.
 */
public class ConventionInspectionTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new ConventionInspection());
        // Start each test from the shipped defaults regardless of state left by prior tests.
        SpringDebuggerSettings s = SpringDebuggerSettings.getInstance();
        s.setConventionsEnabled(true);
        s.getConventionRuleOverrides().clear();
    }

    public void testSquiggleLandsOnPublicMethodName() {
        myFixture.configureByText("Sample.java",
                "public class Sample { public void <warning>doWork</warning>() {} }");
        myFixture.checkHighlighting(true, false, false);
    }

    public void testDocumentedMethodHasNoWarning() {
        myFixture.configureByText("Sample.java",
                "public class Sample { /** Does it. */ public void doWork() {} }");
        myFixture.checkHighlighting(true, false, false);
    }

    public void testFeatureKillSwitchSuppressesHighlight() {
        SpringDebuggerSettings.getInstance().setConventionsEnabled(false);
        // No <warning> markup: with the feature off there must be no highlight.
        myFixture.configureByText("Sample.java",
                "public class Sample { public void doWork() {} }");
        myFixture.checkHighlighting(true, false, false);
    }

    public void testPerRuleToggleSuppressesHighlight() {
        SpringDebuggerSettings.getInstance().setConventionRuleEnabled("JAVADOC_METHOD", false);
        myFixture.configureByText("Sample.java",
                "public class Sample { public void doWork() {} }");
        myFixture.checkHighlighting(true, false, false);
    }
}
