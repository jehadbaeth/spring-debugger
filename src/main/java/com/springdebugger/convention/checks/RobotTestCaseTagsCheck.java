package com.springdebugger.convention.checks;

import com.intellij.psi.PsiFile;
import com.springdebugger.convention.ConventionCheck;
import com.springdebugger.convention.ConventionRule;
import com.springdebugger.convention.Violation;
import com.springdebugger.convention.robot.RobotSuite;
import com.springdebugger.convention.robot.RobotSuiteParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Flags each Robot test case missing [Tags], or (when {@code requireRequirementTag} is true) missing
 * a tag that links it to a requirement.
 *
 * <p>Params: {@code requirementPattern} (regex a tag must match to count as a requirement link,
 * default {@code REQ-.+}); {@code requireRequirementTag} (default true).
 */
public final class RobotTestCaseTagsCheck implements ConventionCheck {

    private static final String DEFAULT_REQ_PATTERN = "REQ-.+";

    @Override
    public String checkType() { return "robotTestCaseTags"; }

    @Override
    public List<Violation> check(PsiFile file, ConventionRule rule) {
        RobotSuite suite = RobotSuiteParser.parse(file.getText());
        boolean requireReqTag = RobotChecks.boolParam(rule, "requireRequirementTag", true);

        final Pattern reqPattern = compileOrDefault(
                RobotChecks.stringParam(rule, "requirementPattern", DEFAULT_REQ_PATTERN));

        List<Violation> out = new ArrayList<>();
        for (RobotSuite.TestCase tc : suite.testCases) {
            boolean violates;
            if (!tc.hasTags) {
                violates = true;
            } else if (requireReqTag) {
                violates = tc.tags.stream().noneMatch(t -> reqPattern.matcher(t).matches());
            } else {
                violates = false;
            }
            if (violates) {
                out.add(new Violation(file, tc.nameRange,
                        RobotChecks.interpolate(rule.getMessage(), "test", tc.name),
                        RobotChecks.interpolate(rule.getFix(), "test", tc.name)));
            }
        }
        return out;
    }

    private static Pattern compileOrDefault(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            return Pattern.compile(Pattern.quote("REQ-"));
        }
    }
}

