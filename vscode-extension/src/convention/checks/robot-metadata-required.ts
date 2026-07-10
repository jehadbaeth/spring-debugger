// Port of com.springdebugger.convention.checks.RobotMetadataRequiredCheck.
import { ConventionCheck } from '../convention-check';
import { ConventionRule } from '../convention-rule';
import { interpolate, stringList } from '../rule-params';
import { findMetadata, TextRange } from '../robot-suite';
import { parseRobotSuite } from '../robot-suite-parser';
import { Violation } from '../violation';

const DEFAULT_REQUIRED = ['Test ID', 'Test Description', 'Pass-Fail Criteria'];

export class RobotMetadataRequiredCheck implements ConventionCheck {
  checkType(): string {
    return 'robotMetadataRequired';
  }

  check(text: string, _fileName: string, rule: ConventionRule): Violation[] {
    const suite = parseRobotSuite(text);
    if (!suite.hasTestCasesSection) return [];

    const anchor: TextRange = suite.settingsHeaderRange ?? firstLineRange(text);

    const out: Violation[] = [];
    for (const required of stringList(rule, 'requiredMetadata', DEFAULT_REQUIRED)) {
      if (findMetadata(suite, required) === null) {
        out.push({
          range: anchor,
          message: interpolate(rule.message, { field: required }),
          fix: interpolate(rule.fix, { field: required }),
        });
      }
    }
    return out;
  }
}

function firstLineRange(text: string): TextRange {
  let end = text.indexOf('\n');
  if (end < 0) end = text.length;
  if (end > 80) end = 80;
  return { start: 0, end: Math.max(0, end) };
}
