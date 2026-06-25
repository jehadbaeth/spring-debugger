// Port of com.springdebugger.rule.RuleCatalog. Loads the SHARED spring-boot-rules.yaml (the same
// file the IntelliJ plugin uses) and maps it to Rule[]. The mapping mirrors the Java raw-Map
// approach field for field so the two engines build identical rule objects.
import * as yaml from 'js-yaml';
import { Confidence, Phase } from './models';
import { Rule, SignalCriteria } from './rule';

export class RuleCatalog {
  private constructor(
    private readonly rules: Rule[],
    private readonly byIdMap: Map<string, Rule>,
  ) {}

  static fromYaml(content: string): RuleCatalog {
    const root = yaml.load(content) as Record<string, unknown> | null;
    const rawRules = (root?.['rules'] as Array<Record<string, unknown>>) ?? [];
    const rules = rawRules.map(mapToRule);
    const byId = new Map<string, Rule>();
    for (const r of rules) byId.set(r.id, r);
    return new RuleCatalog(rules, byId);
  }

  all(): Rule[] {
    return this.rules;
  }

  byId(id: string): Rule | undefined {
    return this.byIdMap.get(id);
  }

  size(): number {
    return this.rules.length;
  }

  /** Number of validated, active rules (status DONE). This is what the engine actually evaluates. */
  activeCount(): number {
    return this.rules.filter((r) => r.status === 'DONE').length;
  }
}

function mapToRule(m: Record<string, unknown>): Rule {
  const phaseStrs = m['phases'] as string[] | undefined;
  const signalsMap = m['signals'] as Record<string, unknown> | undefined;
  const confStr = str(m, 'confidence');
  return {
    id: str(m, 'id') ?? '',
    name: str(m, 'name'),
    status: str(m, 'status'),
    diagnosis: str(m, 'diagnosis'),
    fix: str(m, 'fix'),
    fixture: str(m, 'fixture'),
    confidence: (confStr as Confidence) ?? 'MEDIUM',
    phases: phaseStrs ? phaseStrs.map((p) => p as Phase) : null,
    taps: (m['taps'] as string[] | undefined) ?? [],
    signals: signalsMap ? mapToSignals(signalsMap) : null,
  };
}

function mapToSignals(m: Record<string, unknown>): SignalCriteria {
  const any = m['messageContainsAny'];
  const httpStatus = m['httpStatus'];
  return {
    causedByClass: str(m, 'causedByClass'),
    causedByMessage: str(m, 'causedByMessage'),
    messageContains: str(m, 'messageContains'),
    messageContainsAny: Array.isArray(any) ? (any as string[]) : null,
    bannerDescriptionContains: str(m, 'bannerDescriptionContains'),
    bannerActionContains: str(m, 'bannerActionContains'),
    buildLineContains: str(m, 'buildLineContains'),
    exceptionClass: str(m, 'exceptionClass'),
    httpStatus: typeof httpStatus === 'number' ? httpStatus : 0,
  };
}

/** Mirrors RuleCatalog.str(): value.toString() when present, else null. */
function str(m: Record<string, unknown>, key: string): string | null {
  const v = m[key];
  return v !== undefined && v !== null ? String(v) : null;
}
