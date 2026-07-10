// Port of com.springdebugger.convention.checks.TransactionalMisplacedCheck.
import { ConventionCheck } from '../convention-check';
import { ConventionRule } from '../convention-rule';
import { interpolate, stringSet } from '../rule-params';
import { parseJavaFile, JavaClassInfo } from '../java-members';
import { maskJava } from '../mask-java';
import { Violation } from '../violation';

export class TransactionalMisplacedCheck implements ConventionCheck {
  checkType(): string {
    return 'transactionalMisplaced';
  }

  check(text: string, _fileName: string, rule: ConventionRule): Violation[] {
    const transactionalNames = stringSet(rule, 'transactionalAnnotations', new Set(['Transactional']));
    const excludedNames = stringSet(rule, 'excludedAnnotations', new Set(['RestController', 'Repository']));
    const facts = parseJavaFile(text, maskJava(text));

    const out: Violation[] = [];
    for (let i = 0; i < facts.classes.length; i++) {
      const psiClass = facts.classes[i];
      const excludedMarker = excludedMarkerOf(psiClass, excludedNames);
      if (excludedMarker === null) continue;

      const classTransactional = psiClass.annotations.find((a) => transactionalNames.has(a.name));
      if (classTransactional) {
        out.push(violation(rule, classTransactional.range, psiClass.name, excludedMarker));
      }

      for (const method of facts.methods) {
        if (method.enclosingClassIndex !== i) continue;
        const methodTransactional = method.annotations.find((a) => transactionalNames.has(a.name));
        if (methodTransactional) {
          out.push(violation(rule, methodTransactional.range, method.name, excludedMarker));
        }
      }
    }
    return out;
  }
}

function excludedMarkerOf(psiClass: JavaClassInfo, excludedNames: Set<string>): string | null {
  for (const annotation of psiClass.annotations) {
    if (excludedNames.has(annotation.name)) return annotation.name;
  }
  return null;
}

function violation(
  rule: ConventionRule,
  range: { start: number; end: number },
  memberName: string,
  marker: string,
): Violation {
  return {
    range,
    message: interpolate(rule.message, { member: memberName, marker }),
    fix: interpolate(rule.fix, { member: memberName, marker }),
  };
}
