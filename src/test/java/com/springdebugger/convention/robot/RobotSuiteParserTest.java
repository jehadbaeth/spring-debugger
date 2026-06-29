package com.springdebugger.convention.robot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure parser tests over realistically-shaped Robot suites (tabs, comments, continuations, case). */
class RobotSuiteParserTest {

    @Test
    void parsesMetadataTestCasesDocsAndTags() {
        String text = String.join("\n",
                "*** Settings ***",
                "Library    RequestsLibrary",
                "Metadata    Test Level    Integration",
                "Metadata    Test ID    T-ST-0001",
                "# a comment line",
                "Metadata    Pass-Fail Criteria    Each endpoint returns 200",
                "",
                "*** Variables ***",
                "${BASE_URL}    http://localhost:8083",
                "",
                "*** Test Cases ***",
                "First Test",
                "    [Tags]    REQ-Test-123    smoke",
                "    [Documentation]    Does a thing",
                "    Log    hi",
                "",
                "Second Test",
                "    Log    no docs no tags");

        RobotSuite suite = RobotSuiteParser.parse(text);

        assertThat(suite.hasTestCasesSection).isTrue();
        assertThat(suite.settingsHeaderRange).isNotNull();
        assertThat(suite.findMetadata("Test ID").value).isEqualTo("T-ST-0001");
        // normalization: "Pass-Fail Criteria" matches the "Pass Fail Criteria" wording too
        assertThat(suite.findMetadata("Pass Fail Criteria")).isNotNull();
        assertThat(suite.findMetadata("Missing One")).isNull();

        assertThat(suite.testCases).hasSize(2);
        RobotSuite.TestCase first = suite.testCases.get(0);
        assertThat(first.name).isEqualTo("First Test");
        assertThat(first.hasDocumentation).isTrue();
        assertThat(first.hasTags).isTrue();
        assertThat(first.tags).contains("REQ-Test-123", "smoke");
        assertThat(first.nameRange).isNotNull();

        RobotSuite.TestCase second = suite.testCases.get(1);
        assertThat(second.hasDocumentation).isFalse();
        assertThat(second.hasTags).isFalse();
    }

    @Test
    void handlesTabSeparatorAndLowercaseHeader() {
        String text = String.join("\n",
                "*** settings ***",
                "Metadata\tTest ID\tT-FG-0004",
                "",
                "*** test cases ***",
                "Only Test",
                "\t[Documentation]\thi");

        RobotSuite suite = RobotSuiteParser.parse(text);
        assertThat(suite.findMetadata("Test ID").value).isEqualTo("T-FG-0004");
        assertThat(suite.testCases).hasSize(1);
        assertThat(suite.testCases.get(0).hasDocumentation).isTrue();
    }

    @Test
    void gathersTagsFromContinuationLines() {
        String text = String.join("\n",
                "*** Test Cases ***",
                "T",
                "    [Tags]    REQ-1",
                "    ...    REQ-2",
                "    Log    x");

        RobotSuite suite = RobotSuiteParser.parse(text);
        assertThat(suite.testCases.get(0).tags).contains("REQ-1", "REQ-2");
    }

    @Test
    void resourceFileWithNoTestCasesHasNoSection() {
        String text = String.join("\n",
                "*** Keywords ***",
                "My Keyword",
                "    Log    reusable");

        RobotSuite suite = RobotSuiteParser.parse(text);
        assertThat(suite.hasTestCasesSection).isFalse();
        assertThat(suite.testCases).isEmpty();
    }
}
