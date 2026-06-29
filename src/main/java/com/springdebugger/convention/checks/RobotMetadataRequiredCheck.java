package com.springdebugger.convention.checks;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.springdebugger.convention.ConventionCheck;
import com.springdebugger.convention.ConventionRule;
import com.springdebugger.convention.Violation;
import com.springdebugger.convention.robot.RobotSuite;
import com.springdebugger.convention.robot.RobotSuiteParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Flags a Robot suite whose *** Settings *** is missing required Metadata entries (default Test ID,
 * Test Description, Pass-Fail Criteria). Only fires on files that have a *** Test Cases *** section,
 * so resource/keyword/variable .robot files are left alone.
 *
 * <p>Param {@code requiredMetadata}: list of metadata names that must be present. Matching ignores
 * case, spaces, and dashes, so "Pass-Fail Criteria" and "Pass Fail Criteria" are the same field.
 */
public final class RobotMetadataRequiredCheck implements ConventionCheck {

    private static final List<String> DEFAULT_REQUIRED =
            List.of("Test ID", "Test Description", "Pass-Fail Criteria");

    @Override
    public String checkType() { return "robotMetadataRequired"; }

    @Override
    public List<Violation> check(PsiFile file, ConventionRule rule) {
        String text = file.getText();
        RobotSuite suite = RobotSuiteParser.parse(text);
        if (!suite.hasTestCasesSection) return List.of();

        TextRange anchor = suite.settingsHeaderRange != null
                ? suite.settingsHeaderRange : RobotChecks.firstLineRange(text);

        List<Violation> out = new ArrayList<>();
        for (String required : RobotChecks.stringList(rule, "requiredMetadata", DEFAULT_REQUIRED)) {
            if (suite.findMetadata(required) == null) {
                out.add(new Violation(file, anchor,
                        RobotChecks.interpolate(rule.getMessage(), "field", required),
                        RobotChecks.interpolate(rule.getFix(), "field", required)));
            }
        }
        return out;
    }
}
