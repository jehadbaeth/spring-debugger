// Port of com.springdebugger.convention.ConventionCatalog. Loads the SHARED conventions.yaml (the
// same file the IntelliJ plugin uses) and maps it to ConventionRule[], mirroring the Java raw-Map
// approach field for field so the two engines build identical rule objects.
import * as yaml from 'js-yaml';
import { ConventionRule } from './convention-rule';

export class ConventionCatalog {
  private constructor(
    private readonly rules: ConventionRule[],
    private readonly byIdMap: Map<string, ConventionRule>,
  ) {}

  static fromYaml(content: string): ConventionCatalog {
    const root = yaml.load(content) as Record<string, unknown> | null;
    const rawRules = (root?.['rules'] as Array<Record<string, unknown>>) ?? [];
    const rules = rawRules.map(mapToRule);
    const byId = new Map<string, ConventionRule>();
    for (const r of rules) byId.set(r.id, r);
    return new ConventionCatalog(rules, byId);
  }

  all(): ConventionRule[] {
    return this.rules;
  }

  byId(id: string): ConventionRule | undefined {
    return this.byIdMap.get(id);
  }

  size(): number {
    return this.rules.length;
  }
}

function mapToRule(m: Record<string, unknown>): ConventionRule {
  const enabled = m['enabled'];
  const severity = str(m, 'severity');
  const appliesTo = m['appliesTo'];
  const params = m['params'];
  return {
    id: str(m, 'id') ?? '',
    name: str(m, 'name'),
    checkType: str(m, 'checkType') ?? '',
    enabled: enabled === undefined || enabled === null || Boolean(enabled),
    severity: severity ?? 'WARNING',
    appliesTo: Array.isArray(appliesTo) ? (appliesTo as string[]) : null,
    params: params && typeof params === 'object' ? (params as Record<string, unknown>) : null,
    message: str(m, 'message'),
    fix: str(m, 'fix'),
    status: str(m, 'status'),
  };
}

/** Mirrors ConventionCatalog.str(): value.toString() when present, else null. */
function str(m: Record<string, unknown>, key: string): string | null {
  const v = m[key];
  return v !== undefined && v !== null ? String(v) : null;
}
