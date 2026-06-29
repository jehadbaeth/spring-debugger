package com.springdebugger.convention.checks;

import com.intellij.psi.PsiFile;
import com.springdebugger.convention.ConventionCheck;
import com.springdebugger.convention.ConventionRule;
import com.springdebugger.convention.Violation;
import com.springdebugger.convention.robot.RobotSuite;
import com.springdebugger.convention.robot.RobotSuiteParser;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Flags a Robot suite whose {@code Metadata  Test ID} value does not match the required format. Does
 * nothing when no Test ID is present (the metadata-required check covers absence).
 *
 * <p>Param {@code idPattern}: a regex the whole Test ID value must match. The default follows the
 * convention document's normative section ({@code T-<Scope>-<4 digits>}); the scope set and digit
 * count are deliberately a param because the document's own examples diverge from it.
 */
public final class RobotTestIdFormatCheck implements ConventionCheck {

    private static final String DEFAULT_PATTERN = "T-(SYS|ST|CA|FG|RE)-\\d{4}";

    @Override
    public String checkType() { return "robotTestIdFormat"; }

    @Override
    public List<Violation> check(PsiFile file, ConventionRule rule) {
        RobotSuite suite = RobotSuiteParser.parse(file.getText());
        if (!suite.hasTestCasesSection) return List.of();

        RobotSuite.Metadata testId = suite.findMetadata("Test ID");
        if (testId == null) return List.of();

        Pattern pattern;
        try {
            pattern = Pattern.compile(RobotChecks.stringParam(rule, "idPattern", DEFAULT_PATTERN));
        } catch (PatternSyntaxException e) {
            return List.of(); // a malformed rule pattern must not crash the inspection
        }
        if (pattern.matcher(testId.value.trim()).matches()) return List.of();

        return List.of(new Violation(file, testId.lineRange,
                RobotChecks.interpolate(rule.getMessage(), "value", testId.value.trim()),
                RobotChecks.interpolate(rule.getFix(), "value", testId.value.trim())));
    }
}
