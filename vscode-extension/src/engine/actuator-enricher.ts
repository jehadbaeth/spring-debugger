// Port of com.springdebugger.enricher.ActuatorEnricher. When a runtime error leaves the app alive
// and it exposes Actuator, confirm the diagnosis against live /actuator/health. Strictly additive:
// only sharpens an uncertain runtime card, never weakens or blocks the offline result. Runs only at
// RUNTIME phase and only when the app is reachable.
import { Enricher, EnrichmentContext } from './enricher';
import { DiagnosisCard, RawSignal } from './models';
import { firstDownComponent, overallHealth } from './actuator-reader';

export class ActuatorEnricher implements Enricher {
  async enrich(card: DiagnosisCard, signal: RawSignal, context: EnrichmentContext): Promise<DiagnosisCard> {
    if (signal.phase !== 'RUNTIME') return card;

    const health = await safeGet(context, '/actuator/health');
    if (health === null) return card;

    const status = overallHealth(health);
    if (status === null || status === 'UP') return card;

    const component = firstDownComponent(health) ?? 'a dependency';
    const live = ` Live /actuator/health reports ${status} with '${component}' down.`;

    return {
      ruleId: card.ruleId,
      phase: card.phase,
      diagnosisSentence: card.diagnosisSentence + live,
      fixSentence: card.fixSentence,
      confidence: 'HIGH',
      excerpt: card.excerpt,
    };
  }
}

async function safeGet(context: EnrichmentContext, path: string): Promise<string | null> {
  try {
    return await context.httpGet(path);
  } catch {
    return null;
  }
}
