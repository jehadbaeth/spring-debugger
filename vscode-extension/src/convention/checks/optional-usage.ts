// Port of com.springdebugger.convention.checks.OptionalUsageCheck.
import { ConventionCheck } from '../convention-check';
import { ConventionRule } from '../convention-rule';
import { interpolate, stringParam } from '../rule-params';
import { parseJavaFile } from '../java-members';
import { maskJava } from '../mask-java';
import { Violation } from '../violation';

function isOptionalType(type: string): boolean {
  const simple = type.includes('<') ? type.substring(0, type.indexOf('<')) : type;
  const name = simple.includes('.') ? simple.substring(simple.lastIndexOf('.') + 1) : simple;
  return name === 'Optional';
}

export class OptionalUsageCheck implements ConventionCheck {
  checkType(): string {
    return 'optionalUsage';
  }

  check(text: string, _fileName: string, rule: ConventionRule): Violation[] {
    const target = stringParam(rule, 'target', 'parameter');
    const facts = parseJavaFile(text, maskJava(text));
    const out: Violation[] = [];

    if (target === 'field') {
      for (const field of facts.fields) {
        if (isOptionalType(field.type)) {
          out.push({
            range: field.nameRange,
            message: interpolate(rule.message, { name: field.name }),
            fix: interpolate(rule.fix, { name: field.name }),
          });
        }
      }
    } else {
      for (const method of facts.methods) {
        for (const param of method.parameters) {
          if (isOptionalType(param.type)) {
            out.push({
              range: param.nameRange,
              message: interpolate(rule.message, { name: param.name }),
              fix: interpolate(rule.fix, { name: param.name }),
            });
          }
        }
      }
    }
    return out;
  }
}
