// Port of com.springdebugger.llm.LlmDiagnosisEngine. Used only when the rule engine returns no
// match. Builds a tightly scoped prompt from the signal, asks a local Ollama model for a one-sentence
// diagnosis and fix as JSON, and parses the reply into a card labelled ruleId="llm" at MEDIUM
// confidence. Safety contract: if the reply cannot be parsed into BOTH fields, nothing is shown.
import { DiagnosisEngine } from './enricher';
import { DiagnosisCard, RawSignal } from './models';
import { OllamaClient } from './ollama-http-client';

export class LlmDiagnosisEngine implements DiagnosisEngine {
  constructor(private readonly client: OllamaClient) {}

  async diagnose(signal: RawSignal): Promise<DiagnosisCard | null> {
    if (signal === null || signal === undefined) return null;
    const reply = await this.client.generate(buildPrompt(signal));
    return reply === null ? null : parseCard(reply, signal);
  }
}

export function buildPrompt(signal: RawSignal): string {
  let sb =
    'You are a Spring Boot error triage assistant. ' +
    'Given the extracted signal from a failing Spring Boot application, ' +
    'respond ONLY with a JSON object of exactly two string fields: ' +
    '"diagnosis" (one sentence naming the precise problem) and ' +
    '"fix" (one sentence giving the single corrective action). ' +
    'Do not include any other text.\n\n';
  sb += 'Phase: ' + signal.phase + '\n';
  if (signal.deepestCausedByClass !== null) sb += 'Deepest exception: ' + signal.deepestCausedByClass + '\n';
  if (signal.deepestCausedByMessage !== null) sb += 'Exception message: ' + signal.deepestCausedByMessage + '\n';
  if (signal.bannerDescription !== null) sb += 'Failure analyzer description: ' + signal.bannerDescription + '\n';
  if (signal.bannerAction !== null) sb += 'Failure analyzer action: ' + signal.bannerAction + '\n';
  if (signal.failingBeanName !== null) sb += 'Failing bean: ' + signal.failingBeanName + '\n';
  sb += '\nLog excerpt:\n' + truncate(signal.rawExcerpt, 4000);
  return sb;
}

/** Parses the model's JSON reply. Returns null unless BOTH diagnosis and fix are present. */
export function parseCard(reply: string | null, signal: RawSignal): DiagnosisCard | null {
  if (reply === null || reply.trim() === '') return null;
  try {
    const parsed = JSON.parse(reply);
    if (!parsed || typeof parsed !== 'object') return null;
    const diagnosis = (parsed as Record<string, unknown>).diagnosis;
    const fix = (parsed as Record<string, unknown>).fix;
    if (typeof diagnosis !== 'string' || typeof fix !== 'string') return null;
    if (diagnosis.trim() === '' || fix.trim() === '') return null;
    return {
      ruleId: 'llm',
      phase: signal.phase,
      diagnosisSentence: diagnosis.trim(),
      fixSentence: fix.trim(),
      confidence: 'MEDIUM',
      excerpt: signal.rawExcerpt,
    };
  } catch {
    return null;
  }
}

function truncate(s: string | null, max: number): string {
  if (s === null) return '';
  return s.length <= max ? s : s.substring(0, max) + '\n...[truncated]';
}
