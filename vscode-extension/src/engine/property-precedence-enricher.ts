// Port of com.springdebugger.enricher.PropertyPrecedenceEnricher. When a runtime config error names
// a property key and the app is alive with Actuator, report which property source actually supplies
// the effective value via /actuator/env/{key}. Strictly additive; confidence left unchanged.
import { Enricher, EnrichmentContext } from './enricher';
import { DiagnosisCard, RawSignal } from './models';
import { effectivePropertySource } from './actuator-reader';

const PROPERTY_KEY = /[a-z][a-z0-9]*(?:\.[a-z0-9-]+)+/;

export class PropertyPrecedenceEnricher implements Enricher {
  async enrich(card: DiagnosisCard, signal: RawSignal, context: EnrichmentContext): Promise<DiagnosisCard> {
    if (signal.phase !== 'RUNTIME') return card;

    let key = firstPropertyKey(signal.deepestCausedByMessage);
    if (key === null) key = firstPropertyKey(card.diagnosisSentence);
    if (key === null) return card;

    const env = await safeGet(context, '/actuator/env/' + key);
    if (env === null) return card;

    const source = effectivePropertySource(env);
    if (source === null) return card;

    return {
      ruleId: card.ruleId,
      phase: card.phase,
      diagnosisSentence: `${card.diagnosisSentence} The effective value of '${key}' comes from ${source}.`,
      fixSentence: card.fixSentence,
      confidence: card.confidence,
      excerpt: card.excerpt,
    };
  }
}

export function firstPropertyKey(text: string | null): string | null {
  if (text === null) return null;
  const m = PROPERTY_KEY.exec(text);
  return m ? m[0] : null;
}

async function safeGet(context: EnrichmentContext, path: string): Promise<string | null> {
  try {
    return await context.httpGet(path);
  } catch {
    return null;
  }
}
