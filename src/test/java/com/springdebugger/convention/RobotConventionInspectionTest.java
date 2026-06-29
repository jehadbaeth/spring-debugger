package com.springdebugger.convention;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.springdebugger.settings.SpringDebuggerSettings;

/**
 * Proves the full inspection path on a .robot file: the squiggle lands on the offending element. The
 * suite below is compliant on every rule except the missing [Documentation], so exactly one warning
 * is expected. (The .robot file type association in a real IDE is verified by hand; this confirms the
 * highlight machinery for plain-text files.)
 */
public class RobotConventionInspectionTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new ConventionInspection());
        SpringDebuggerSettings s = SpringDebuggerSettings.getInstance();
        s.setConventionsEnabled(true);
        s.getConventionRuleOverrides().clear();
    }

    public void testMissingDocumentationHighlightsTestCaseName() {
        myFixture.configureByText("Suite.robot", String.join("\n",
                "*** Settings ***",
                "Metadata    Test ID    T-ST-0001",
                "Metadata    Test Description    desc",
                "Metadata    Pass-Fail Criteria    crit",
                "",
                "*** Test Cases ***",
                "<warning>My Test</warning>",
                "    [Tags]    REQ-1",
                "    Log    hi",
                ""));
        myFixture.checkHighlighting(true, false, false);
    }
}
