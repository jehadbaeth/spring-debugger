// Port of com.springdebugger.convention.checks.RequestBodyRequiresValidCheck.
import { ConventionCheck } from '../convention-check';
import { ConventionRule } from '../convention-rule';
import { interpolate, stringParam, stringSet } from '../rule-params';
import { parseJavaFile } from '../java-members';
import { maskJava } from '../mask-java';
import { Violation } from '../violation';

export class RequestBodyRequiresValidCheck implements ConventionCheck {
  checkType(): string {
    return 'requestBodyRequiresValid';
  }

  check(text: string, _fileName: string, rule: ConventionRule): Violation[] {
    const bodyAnnotation = stringParam(rule, 'bodyAnnotation', 'RequestBody');
    const validationAnnotations = stringSet(rule, 'validationAnnotations', new Set(['Valid', 'Validated']));
    const facts = parseJavaFile(text, maskJava(text));

    const out: Violation[] = [];
    for (const method of facts.methods) {
      for (const param of method.parameters) {
        if (!param.annotations.some((a) => a.name === bodyAnnotation)) continue;
        if (param.annotations.some((a) => validationAnnotations.has(a.name))) continue;

        out.push({
          range: param.nameRange,
          message: interpolate(rule.message, { parameter: param.name }),
          fix: interpolate(rule.fix, { parameter: param.name }),
        });
      }
    }
    return out;
  }
}
