// Port of com.springdebugger.convention.checks.NoSystemOutErrCheck. Matched textually (no PSI
// resolution needed on the Java side either), so a masked-text scan is a faithful port.
import { ConventionCheck } from '../convention-check';
import { ConventionRule } from '../convention-rule';
import { interpolate } from '../rule-params';
import { maskJava } from '../mask-java';
import { Violation } from '../violation';

const SYSTEM_OUT_ERR = /\bSystem\s*\.\s*(out|err)\b/g;

export class NoSystemOutErrCheck implements ConventionCheck {
  checkType(): string {
    return 'noSystemOutErr';
  }

  check(text: string, _fileName: string, rule: ConventionRule): Violation[] {
    const masked = maskJava(text);
    const out: Violation[] = [];
    SYSTEM_OUT_ERR.lastIndex = 0;
    let m: RegExpExecArray | null;
    while ((m = SYSTEM_OUT_ERR.exec(masked)) !== null) {
      const member = m[1];
      out.push({
        range: { start: m.index, end: m.index + m[0].length },
        message: interpolate(rule.message, { member }),
        fix: interpolate(rule.fix, { member }),
      });
    }
    return out;
  }
}
