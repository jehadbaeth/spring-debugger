// Port of com.springdebugger.convention.checks.JavadocRequiredCheck.
import { ConventionCheck } from '../convention-check';
import { ConventionRule } from '../convention-rule';
import { interpolate, boolParam } from '../rule-params';
import { parseJavaFile, JavaMethod } from '../java-members';
import { maskJava } from '../mask-java';
import { Violation } from '../violation';

export class JavadocRequiredCheck implements ConventionCheck {
  checkType(): string {
    return 'javadocRequired';
  }

  check(text: string, _fileName: string, rule: ConventionRule): Violation[] {
    const visibilities = stringSetParam(rule, 'visibilities', new Set(['public']));
    const skipOverrides = boolParam(rule, 'skipOverrides', true);
    const skipAccessors = boolParam(rule, 'skipAccessors', true);

    const facts = parseJavaFile(text, maskJava(text));
    const out: Violation[] = [];
    for (const method of facts.methods) {
      if (!visibilityMatches(method, visibilities)) continue;
      if (skipOverrides && method.annotations.some((a) => a.name === 'Override')) continue;
      if (skipAccessors && isAccessor(method)) continue;
      if (method.hasJavadoc) continue;

      out.push({
        range: method.nameRange,
        message: interpolate(rule.message, { method: method.name }),
        fix: interpolate(rule.fix, { method: method.name }),
      });
    }
    return out;
  }
}

function stringSetParam(rule: ConventionRule, key: string, dflt: Set<string>): Set<string> {
  const v = rule.params?.[key];
  if (Array.isArray(v) && v.length > 0) return new Set(v.map((o) => String(o)));
  return dflt;
}

function visibilityMatches(m: JavaMethod, vis: Set<string>): boolean {
  if (m.modifiers.includes('public')) return vis.has('public');
  if (m.modifiers.includes('protected')) return vis.has('protected');
  if (m.modifiers.includes('private')) return vis.has('private');
  return vis.has('package');
}

function isAccessor(m: JavaMethod): boolean {
  const n = m.name;
  const paramCount = m.parameters.length;
  const isVoid = m.returnType === 'void';
  if (n.startsWith('get') && n.length > 3 && paramCount === 0 && !isVoid) return true;
  if (n.startsWith('is') && n.length > 2 && paramCount === 0 && m.returnType === 'boolean') return true;
  return n.startsWith('set') && n.length > 3 && paramCount === 1 && isVoid;
}
