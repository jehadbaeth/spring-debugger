// Port of com.springdebugger.convention.checks.FieldInjectionForbiddenCheck.
import { ConventionCheck } from '../convention-check';
import { ConventionRule } from '../convention-rule';
import { interpolate, stringSet } from '../rule-params';
import { parseJavaFile } from '../java-members';
import { maskJava } from '../mask-java';
import { Violation } from '../violation';

export class FieldInjectionForbiddenCheck implements ConventionCheck {
  checkType(): string {
    return 'fieldInjectionForbidden';
  }

  check(text: string, _fileName: string, rule: ConventionRule): Violation[] {
    const annotationNames = stringSet(rule, 'annotations', new Set(['Autowired', 'Inject']));
    const facts = parseJavaFile(text, maskJava(text));

    const out: Violation[] = [];
    for (const field of facts.fields) {
      if (field.annotations.some((a) => annotationNames.has(a.name))) {
        out.push({
          range: field.nameRange,
          message: interpolate(rule.message, { field: field.name }),
          fix: interpolate(rule.fix, { field: field.name }),
        });
      }
    }
    return out;
  }
}
