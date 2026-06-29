package com.springdebugger.convention.checks;

import com.intellij.psi.PsiFile;
import com.springdebugger.convention.ConventionCheck;
import com.springdebugger.convention.ConventionRule;
import com.springdebugger.convention.Violation;
import com.springdebugger.convention.robot.RobotSuite;
import com.springdebugger.convention.robot.RobotSuiteParser;

import java.util.ArrayList;
import java.util.List;

/** Flags each Robot test case that has no [Documentation] setting. */
public final class RobotTestCaseDocumentationCheck implements ConventionCheck {

    @Override
    public String checkType() { return "robotTestCaseDoc"; }

    @Override
    public List<Violation> check(PsiFile file, ConventionRule rule) {
        RobotSuite suite = RobotSuiteParser.parse(file.getText());
        List<Violation> out = new ArrayList<>();
        for (RobotSuite.TestCase tc : suite.testCases) {
            if (!tc.hasDocumentation) {
                out.add(new Violation(file, tc.nameRange,
                        RobotChecks.interpolate(rule.getMessage(), "test", tc.name),
                        RobotChecks.interpolate(rule.getFix(), "test", tc.name)));
            }
        }
        return out;
    }
}
