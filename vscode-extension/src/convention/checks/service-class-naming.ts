// Port of com.springdebugger.convention.checks.ServiceClassNamingCheck.
import { ConventionCheck } from '../convention-check';
import { ConventionRule } from '../convention-rule';
import { interpolate, stringParam } from '../rule-params';
import { parseJavaFile } from '../java-members';
import { maskJava } from '../mask-java';
import { Violation } from '../violation';

export class ServiceClassNamingCheck implements ConventionCheck {
  checkType(): string {
    return 'serviceClassNaming';
  }

  check(text: string, _fileName: string, rule: ConventionRule): Violation[] {
    const serviceAnnotation = stringParam(rule, 'serviceAnnotation', 'Service');
    const suffix = stringParam(rule, 'suffix', 'Service');
    const facts = parseJavaFile(text, maskJava(text));

    const out: Violation[] = [];
    for (const psiClass of facts.classes) {
      if (!psiClass.annotations.some((a) => a.name === serviceAnnotation)) continue;
      if (psiClass.name.endsWith(suffix)) continue;

      out.push({
        range: psiClass.nameRange,
        message: interpolate(rule.message, { class: psiClass.name, suffix }),
        fix: interpolate(rule.fix, { class: psiClass.name, suffix }),
      });
    }
    return out;
  }
}
