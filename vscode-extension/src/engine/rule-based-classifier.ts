// Port of com.springdebugger.classifier.RuleBasedClassifier. Rules are evaluated in catalog order;
// the first DONE rule whose phase matches and whose every present criterion matches wins. Template
// placeholders are filled from the signal exactly as in Java.
import { DiagnosisCard, RawSignal } from './models';
import { RuleCatalog } from './rule-catalog';
import { Rule, SignalCriteria } from './rule';

const PORT = /[Pp]ort[\s:]+(\d{2,5})/;
const PLACEHOLDER = /Could not resolve placeholder ['"]?([\w.\-]+)/;
const OF_TYPE = /of type \[?([\w.$]+)/;

export class RuleBasedClassifier {
  constructor(private readonly catalog: RuleCatalog) {}

  classify(signal: RawSignal): DiagnosisCard | null {
    for (const rule of this.catalog.all()) {
      // Only validated (DONE) rules are active; TODO rules must not fire in production.
      if (rule.status !== 'DONE') continue;
      if (
        rule.phases !== null &&
        rule.phases.length > 0 &&
        !rule.phases.includes(signal.phase)
      ) {
        continue;
      }
      if (this.matches(rule.signals, signal)) {
        return {
          ruleId: rule.id,
          phase: signal.phase,
          diagnosisSentence: this.fillTemplate(rule.diagnosis, signal, rule),
          fixSentence: this.fillTemplate(rule.fix, signal, rule),
          confidence: rule.confidence,
          excerpt: signal.rawExcerpt,
        };
      }
    }
    return null;
  }

  private matches(criteria: SignalCriteria | null, signal: RawSignal): boolean {
    if (criteria === null) return false;
    // A rule with no active signal must never match (it would match everything).
    if (!hasAnySignal(criteria)) return false;

    if (criteria.causedByClass != null) {
      if (signal.deepestCausedByClass === null) return false;
      if (!containsIgnoreCase(signal.deepestCausedByClass, criteria.causedByClass)) return false;
    }

    if (criteria.causedByMessage != null) {
      if (signal.deepestCausedByMessage === null) return false;
      if (!containsIgnoreCase(signal.deepestCausedByMessage, criteria.causedByMessage)) return false;
    }

    if (criteria.messageContains != null) {
      const inLines = signal.anyLineContains(criteria.messageContains);
      const inExcerpt = containsIgnoreCase(signal.rawExcerpt, criteria.messageContains);
      if (!inLines && !inExcerpt) return false;
    }

    if (criteria.messageContainsAny != null && criteria.messageContainsAny.length > 0) {
      let anyMatch = false;
      for (const alt of criteria.messageContainsAny) {
        if (signal.anyLineContains(alt) || containsIgnoreCase(signal.rawExcerpt, alt)) {
          anyMatch = true;
          break;
        }
      }
      if (!anyMatch) return false;
    }

    if (criteria.bannerDescriptionContains != null) {
      if (signal.bannerDescription === null) return false;
      if (!containsIgnoreCase(signal.bannerDescription, criteria.bannerDescriptionContains)) return false;
    }

    if (criteria.bannerActionContains != null) {
      if (signal.bannerAction === null) return false;
      if (!containsIgnoreCase(signal.bannerAction, criteria.bannerActionContains)) return false;
    }

    if ((criteria.httpStatus ?? 0) > 0) {
      if (signal.httpStatus !== criteria.httpStatus) return false;
    }

    if (criteria.buildLineContains != null) {
      const inLines = signal.anyLineContains(criteria.buildLineContains);
      const inExcerpt = containsIgnoreCase(signal.rawExcerpt, criteria.buildLineContains);
      if (!inLines && !inExcerpt) return false;
    }

    return true;
  }

  private fillTemplate(template: string | null, signal: RawSignal, rule: Rule): string {
    if (template === null) return '';
    let result = template;
    if (result.includes('{{beanType}}')) {
      const source =
        signal.deepestCausedByMessage !== null
          ? signal.deepestCausedByMessage
          : signal.bannerDescription;
      result = result.split('{{beanType}}').join(source !== null ? extractTypeName(source) : 'the required type');
    }
    if (result.includes('{{beanName}}')) {
      result = result
        .split('{{beanName}}')
        .join(signal.failingBeanName !== null ? signal.failingBeanName : 'the failing bean');
    }
    if (result.includes('{{port}}')) {
      result = result.split('{{port}}').join(firstMatch(PORT, signal, 'the configured port'));
    }
    if (result.includes('{{property}}')) {
      result = result.split('{{property}}').join(firstMatch(PLACEHOLDER, signal, 'the property'));
    }
    result = result.split('{{ruleId}}').join(rule.id);
    return result;
  }
}

/** First capture group of the pattern across the deepest message, banner, then excerpt. */
function firstMatch(pattern: RegExp, signal: RawSignal, fallback: string): string {
  for (const text of [signal.deepestCausedByMessage, signal.bannerDescription, signal.rawExcerpt]) {
    if (text === null) continue;
    const m = pattern.exec(text);
    if (m) return m[1];
  }
  return fallback;
}

/**
 * Extracts the bean type name from an exception message. Prefers a quoted fully-qualified name,
 * then an "of type X" form. Mirrors RuleBasedClassifier.extractTypeName.
 */
function extractTypeName(message: string): string {
  const quoted = /'([\w.$]+)'/g;
  let q: RegExpExecArray | null;
  while ((q = quoted.exec(message)) !== null) {
    const candidate = q[1];
    if (candidate.includes('.') || (candidate.length > 0 && isUpperCase(candidate.charAt(0)))) {
      return simpleName(candidate);
    }
  }
  const o = OF_TYPE.exec(message);
  if (o) return simpleName(o[1]);
  return 'the required type';
}

function simpleName(fqn: string): string {
  const dot = fqn.lastIndexOf('.');
  return dot >= 0 ? fqn.substring(dot + 1) : fqn;
}

/** Mirrors Character.isUpperCase for the cased letters that appear in class names. */
function isUpperCase(ch: string): boolean {
  return ch !== ch.toLowerCase() && ch === ch.toUpperCase();
}

function hasAnySignal(c: SignalCriteria): boolean {
  return (
    c.causedByClass != null ||
    c.causedByMessage != null ||
    c.messageContains != null ||
    (c.messageContainsAny != null && c.messageContainsAny.length > 0) ||
    c.bannerDescriptionContains != null ||
    c.bannerActionContains != null ||
    c.buildLineContains != null ||
    c.exceptionClass != null ||
    (c.httpStatus ?? 0) > 0
  );
}

function containsIgnoreCase(text: string, substring: string): boolean {
  return text.toLowerCase().includes(substring.toLowerCase());
}
