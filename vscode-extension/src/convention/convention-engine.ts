// Port of com.springdebugger.convention.ConventionInspection's rule-running loop, minus the PSI
// problem-descriptor plumbing: given a file's text and the active catalog, runs every DONE,
// enabled, applicable rule and returns the violations it found as plain findings a VS Code
// DiagnosticCollection can render directly.
import { ConventionCatalog } from './convention-catalog';
import { appliesToFileType, ConventionRule } from './convention-rule';
import { getCheck } from './check-registry';
import { TextRange } from './robot-suite';

export interface ConventionFinding {
  ruleId: string;
  severity: string;
  range: TextRange;
  message: string;
}

export function runConventions(
  text: string,
  fileName: string,
  catalog: ConventionCatalog,
  isRuleEnabled: (rule: ConventionRule) => boolean,
): ConventionFinding[] {
  const fileType = extensionOf(fileName);
  const out: ConventionFinding[] = [];

  for (const rule of catalog.all()) {
    if (rule.status !== 'DONE') continue;
    if (!isRuleEnabled(rule)) continue;
    if (!appliesToFileType(rule, fileType)) continue;

    const check = getCheck(rule.checkType);
    if (!check) continue;

    for (const violation of check.check(text, fileName, rule)) {
      const message = violation.fix ? violation.message + ' ' + violation.fix : violation.message;
      out.push({ ruleId: rule.id, severity: rule.severity, range: violation.range, message });
    }
  }
  return out;
}

function extensionOf(fileName: string): string {
  const dot = fileName.lastIndexOf('.');
  return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : '';
}
