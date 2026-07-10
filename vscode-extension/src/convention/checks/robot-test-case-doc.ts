// Port of com.springdebugger.convention.checks.RobotTestCaseDocumentationCheck.
import { ConventionCheck } from '../convention-check';
import { ConventionRule } from '../convention-rule';
import { interpolate } from '../rule-params';
import { parseRobotSuite } from '../robot-suite-parser';
import { Violation } from '../violation';

export class RobotTestCaseDocCheck implements ConventionCheck {
  checkType(): string {
    return 'robotTestCaseDoc';
  }

  check(text: string, _fileName: string, rule: ConventionRule): Violation[] {
    const suite = parseRobotSuite(text);
    const out: Violation[] = [];
    for (const tc of suite.testCases) {
      if (!tc.hasDocumentation) {
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
