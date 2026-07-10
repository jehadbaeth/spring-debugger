// Port of com.springdebugger.convention.checks.ApiVersionPathCheck. Only trusts a literal string
// value/path attribute on the mapping annotation; anything else (a constant reference, a
// concatenation, a multi-element array) is silently skipped rather than guessed at.
import { ConventionCheck } from '../convention-check';
import { ConventionRule } from '../convention-rule';
import { interpolate, stringParam, stringSet } from '../rule-params';
import { parseJavaFile } from '../java-members';
import { maskJava } from '../mask-java';
import { Violation } from '../violation';

export class ApiVersionPathCheck implements ConventionCheck {
  checkType(): string {
    return 'apiVersionPath';
  }

  check(text: string, _fileName: string, rule: ConventionRule): Violation[] {
    const mappingAnnotations = stringSet(rule, 'mappingAnnotations', new Set(['RequestMapping']));
    const pathPattern = new RegExp('^(?:' + stringParam(rule, 'pathPattern', '^/api/v\\d+(/.*)?$').replace(/^\^|\$$/g, '') + ')$');
    const facts = parseJavaFile(text, maskJava(text));

    const out: Violation[] = [];
    for (const psiClass of facts.classes) {
      for (const annotation of psiClass.annotations) {
        if (!mappingAnnotations.has(annotation.name)) continue;

        const path = literalPathValue(annotation.args);
        if (path === null || pathPattern.test(path)) continue;

        out.push({
          range: annotation.range,
          message: interpolate(rule.message, { path, class: psiClass.name }),
          fix: interpolate(rule.fix, { path, class: psiClass.name }),
        });
      }
    }
    return out;
  }
}

function literalPathValue(args: string | null): string | null {
  if (args === null) return null;
  const trimmed = args.trim();
  let m = /^"((?:\\.|[^"\\])*)"$/.exec(trimmed);
  if (m) return m[1];
  m = /^\{\s*"((?:\\.|[^"\\])*)"\s*\}$/.exec(trimmed);
  if (m) return m[1];
  m = /(?:^|,)\s*(?:value|path)\s*=\s*"((?:\\.|[^"\\])*)"\s*(?:,|$)/.exec(trimmed);
  if (m) return m[1];
  m = /(?:^|,)\s*(?:value|path)\s*=\s*\{\s*"((?:\\.|[^"\\])*)"\s*\}\s*(?:,|$)/.exec(trimmed);
  if (m) return m[1];
  return null;
}
