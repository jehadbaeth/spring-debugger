// Port of com.springdebugger.convention.checks.RobotTestIdFormatCheck.
import { ConventionCheck } from '../convention-check';
import { ConventionRule } from '../convention-rule';
import { interpolate, stringParam } from '../rule-params';
import { findMetadata } from '../robot-suite';
import { parseRobotSuite } from '../robot-suite-parser';
import { Violation } from '../violation';

const DEFAULT_PATTERN = 'T-(SYS|ST|CA|FG|RE)-\\d{4}';

export class RobotTestIdFormatCheck implements ConventionCheck {
  checkType(): string {
    return 'robotTestIdFormat';
  }

  check(text: string, _fileName: string, rule: ConventionRule): Violation[] {
    const suite = parseRobotSuite(text);
    if (!suite.hasTestCasesSection) return [];

    const testId = findMetadata(suite, 'Test ID');
    if (testId === null) return [];

    let pattern: RegExp;
    try {
      pattern = new RegExp('^(?:' + stringParam(rule, 'idPattern', DEFAULT_PATTERN) + ')$');
    } catch {
      return []; // a malformed rule pattern must not crash the check
    }
    const value = testId.value.trim();
    if (pattern.test(value)) return [];

    return [
      {
        range: testId.lineRange,
        message: interpolate(rule.message, { value }),
        fix: interpolate(rule.fix, { value }),
      },
    ];
  }
}
