// Port of com.springdebugger.convention.checks.EntityStringFieldBoundedCheck.
import { ConventionCheck } from '../convention-check';
import { ConventionRule } from '../convention-rule';
import { interpolate, stringParam, stringSet } from '../rule-params';
import { parseJavaFile } from '../java-members';
import { maskJava } from '../mask-java';
import { Violation } from '../violation';

function isStringType(type: string): boolean {
  const simple = type.includes('<') ? type.substring(0, type.indexOf('<')) : type;
  const name = simple.includes('.') ? simple.substring(simple.lastIndexOf('.') + 1) : simple;
  return name === 'String';
}

export class EntityStringFieldBoundedCheck implements ConventionCheck {
  checkType(): string {
    return 'entityStringFieldBounded';
  }

  check(text: string, _fileName: string, rule: ConventionRule): Violation[] {
    const entityAnnotation = stringParam(rule, 'entityAnnotation', 'Entity');
    const boundingAnnotations = stringSet(rule, 'boundingAnnotations', new Set(['Size', 'Column', 'Convert']));
    const facts = parseJavaFile(text, maskJava(text));

    const entityClassIndexes = new Set(
      facts.classes
        .map((c, i) => ({ c, i }))
        .filter(({ c }) => c.annotations.some((a) => a.name === entityAnnotation))
        .map(({ i }) => i),
    );

    const out: Violation[] = [];
    for (const field of facts.fields) {
      if (!entityClassIndexes.has(field.enclosingClassIndex)) continue;
      if (!isStringType(field.type)) continue;
      if (field.annotations.some((a) => boundingAnnotations.has(a.name))) continue;

      out.push({
        range: field.nameRange,
        message: interpolate(rule.message, { field: field.name }),
        fix: interpolate(rule.fix, { field: field.name }),
      });
    }
    return out;
  }
}
