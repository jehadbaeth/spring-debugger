// Port of com.springdebugger.convention.ConventionRule. One entry in conventions.yaml.
export interface ConventionRule {
  id: string;
  name: string | null;
  checkType: string;
  enabled: boolean;
  severity: string;
  appliesTo: string[] | null;
  params: Record<string, unknown> | null;
  message: string | null;
  fix: string | null;
  status: string | null;
}

/** True if this rule should run against the given file type (by extension). Empty appliesTo = all. */
export function appliesToFileType(rule: ConventionRule, fileType: string): boolean {
  return rule.appliesTo === null || rule.appliesTo.length === 0 || rule.appliesTo.includes(fileType);
}
