// Shared param-reading helpers for the convention checks, mirroring the Java RuleParams /
// RobotChecks helper classes.
import { ConventionRule } from './convention-rule';

export function stringSet(rule: ConventionRule, key: string, dflt: Set<string>): Set<string> {
  const v = rule.params?.[key];
  if (Array.isArray(v) && v.length > 0) {
    return new Set(v.map((o) => String(o)));
  }
  return dflt;
}

export function stringList(rule: ConventionRule, key: string, dflt: string[]): string[] {
  const v = rule.params?.[key];
  if (Array.isArray(v) && v.length > 0) {
    return v.map((o) => String(o));
  }
  return dflt;
}

export function stringParam(rule: ConventionRule, key: string, dflt: string): string {
  const v = rule.params?.[key];
  return v === undefined || v === null ? dflt : String(v);
}

export function boolParam(rule: ConventionRule, key: string, dflt: boolean): boolean {
  const v = rule.params?.[key];
  return v === undefined || v === null ? dflt : v === true || v === 'true';
}

export function interpolate(template: string | null, replacements: Record<string, string>): string {
  if (template === null) return '';
  let out = template;
  for (const [key, value] of Object.entries(replacements)) {
    out = out.split(`{{${key}}}`).join(value);
  }
  return out;
}
