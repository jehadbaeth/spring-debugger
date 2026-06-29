package com.springdebugger.convention;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Drives each Robot check against a real (plain-text) PSI .robot file, using the shipped catalog
 * rules. The Test ID case deliberately uses the convention document's own example T-API-1909 as the
 * failing input, since that example diverges from the document's normative T-<Scope>-<NNNN> format.
 */
public class RobotChecksPsiTest extends LightJavaCodeInsightFixtureTestCase {

    private final ConventionCatalog catalog = ConventionCatalog.load();

    private List<String> messages(String ruleId, String robotSource) {
        PsiFile file = myFixture.configureByText("Suite.robot", robotSource);
        ConventionRule rule = catalog.byId(ruleId);
        ConventionCheck check = CheckRegistry.get(rule.getCheckType());
        return check.check(file, rule).stream().map(Violation::message).collect(Collectors.toList());
    }

    private static String suite(String settingsBody, String testCasesBody) {
        return "*** Settings ***\n" + settingsBody + "\n*** Test Cases ***\n" + testCasesBody;
    }

    // ── ROBOT_METADATA_REQUIRED ──────────────────────────────────────────────

    public void testMissingMetadataIsFlagged() {
        List<String> msgs = messages("ROBOT_METADATA_REQUIRED",
                suite("Metadata    Test ID    T-ST-0001\n", "T\n    Log    hi\n"));
        // Test Description and Pass-Fail Criteria are absent
        assertEquals(2, msgs.size());
        assertTrue(msgs.toString().contains("Test Description"));
        assertTrue(msgs.toString().contains("Pass-Fail Criteria"));
    }

    public void testCompleteMetadataIsClean() {
        List<String> msgs = messages("ROBOT_METADATA_REQUIRED",
                suite("Metadata    Test ID    T-ST-0001\n"
                    + "Metadata    Test Description    desc\n"
                    + "Metadata    Pass-Fail Criteria    crit\n", "T\n    Log    hi\n"));
        assertTrue(msgs.isEmpty());
    }

    public void testResourceFileWithoutTestCasesIsNotFlagged() {
        PsiFile file = myFixture.configureByText("keywords.robot",
                "*** Keywords ***\nMy Keyword\n    Log    reusable\n");
        ConventionRule rule = catalog.byId("ROBOT_METADATA_REQUIRED");
        assertTrue(CheckRegistry.get(rule.getCheckType()).check(file, rule).isEmpty());
    }

    // ── ROBOT_TEST_ID_FORMAT ─────────────────────────────────────────────────

    public void testDocExampleTestIdFailsStrictFormat() {
        // T-API-1909 is from the convention doc's own example, yet violates its normative format.
        List<String> msgs = messages("ROBOT_TEST_ID_FORMAT",
                suite("Metadata    Test ID    T-API-1909\n", "T\n    Log    hi\n"));
        assertEquals(1, msgs.size());
        assertTrue(msgs.get(0).contains("T-API-1909"));
    }

    public void testCompliantTestIdPasses() {
        List<String> msgs = messages("ROBOT_TEST_ID_FORMAT",
                suite("Metadata    Test ID    T-ST-0016\n", "T\n    Log    hi\n"));
        assertTrue(msgs.isEmpty());
    }

    // ── ROBOT_TESTCASE_DOC ───────────────────────────────────────────────────

    public void testTestCaseWithoutDocumentationIsFlagged() {
        List<String> msgs = messages("ROBOT_TESTCASE_DOC",
                suite("Metadata    Test ID    T-ST-0001\n",
                        "Has Doc\n    [Documentation]    ok\n    Log    hi\n\nNo Doc\n    Log    hi\n"));
        assertEquals(1, msgs.size());
        assertTrue(msgs.get(0).contains("No Doc"));
    }

    // ── ROBOT_TESTCASE_TAGS ──────────────────────────────────────────────────

    public void testTestCaseWithoutRequirementTagIsFlagged() {
        List<String> msgs = messages("ROBOT_TESTCASE_TAGS",
                suite("Metadata    Test ID    T-ST-0001\n",
                        "Linked\n    [Tags]    REQ-1\n    Log    hi\n\nUnlinked\n    [Tags]    smoke\n    Log    hi\n"));
        assertEquals(1, msgs.size());
        assertTrue(msgs.get(0).contains("Unlinked"));
    }
}
