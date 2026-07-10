// Port of com.springdebugger.convention.checks.RobotTestCaseTagsCheck.
import { ConventionCheck } from '../convention-check';
import { ConventionRule } from '../convention-rule';
import { boolParam, interpolate, stringParam } from '../rule-params';
import { parseRobotSuite } from '../robot-suite-parser';
import { Violation } from '../violation';

const DEFAULT_REQ_PATTERN = 'REQ-.+';

export class RobotTestCaseTagsCheck implements ConventionCheck {
  checkType(): string {
    return 'robotTestCaseTags';
  }

  check(text: string, _fileName: string, rule: ConventionRule): Violation[] {
    const suite = parseRobotSuite(text);
    const requireReqTag = boolParam(rule, 'requireRequirementTag', true);
    const reqPattern = compileOrDefault(stringParam(rule, 'requirementPattern', DEFAULT_REQ_PATTERN));

    const out: Violation[] = [];
    for (const tc of suite.testCases) {
      let violates: boolean;
      if (!tc.hasTags) {
        violates = true;
      } else if (requireReqTag) {
        violates = !tc.tags.some((t) => reqPattern.test(t));
      } else {
        violates = false;
      }
      if (violates) {
        out.push({
          range: tc.nameRange,
          message: interpolate(rule.message, { test: tc.name }),
          fix: interpolate(rule.fix, { test: tc.name }),
        });
      }
    }
    return out;
  }
}

function compileOrDefault(regex: string): RegExp {
  try {
    return new RegExp('^(?:' + regex + ')$');
  } catch {
    return /^REQ-/;
  }
}
