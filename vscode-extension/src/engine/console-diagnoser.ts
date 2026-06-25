// Port of com.springdebugger.tap.ConsoleDiagnoser (the shipped diagnoseAll entry point both
// products use). Segments a noisy buffer, diagnoses each block through STARTUP -> COMPILE ->
// RUNTIME, and de-duplicates by grouping key preserving first-occurrence order. This is the path
// the parity golden was generated from, so it must match the Java behaviour card for card.
import { BuildOutputAnalyzer } from './build-output-analyzer';
import { DiagnosisEngine, Enricher, EnrichmentContext } from './enricher';
import { extract } from './log-extractor';
import { DiagnosisCard, groupingKey } from './models';
import { RuleBasedClassifier } from './rule-based-classifier';
import { RuleCatalog } from './rule-catalog';
import { segment } from './stack-trace-segmenter';

/** Optional layers for the enriched path. All absent => identical to the sync rule-only path. */
export interface EnrichOptions {
  context?: EnrichmentContext | null;
  enrichers?: Enricher[];
  llm?: DiagnosisEngine | null;
}

export class ConsoleDiagnoser {
  private readonly classifier: RuleBasedClassifier;
  private readonly buildAnalyzer: BuildOutputAnalyzer;

  constructor(private readonly catalog: RuleCatalog) {
    this.classifier = new RuleBasedClassifier(catalog);
    this.buildAnalyzer = new BuildOutputAnalyzer(catalog);
  }

  /**
   * Diagnoses every distinct error in a buffer: split into per-error blocks, diagnose each, and
   * de-duplicate by rule + diagnosis so a repeated error yields one card. Order preserved.
   */
  diagnoseAll(output: string): DiagnosisCard[] {
    const byKey = new Map<string, DiagnosisCard>();
    for (const block of segment(output)) {
      const card = this.diagnoseBlock(block);
      if (card !== null) {
        const k = groupingKey(card);
        if (!byKey.has(k)) byKey.set(k, card); // putIfAbsent: first occurrence wins
      }
    }
    return Array.from(byKey.values());
  }

  diagnose(output: string): DiagnosisCard | null {
    return this.diagnoseBlock(output);
  }

  /**
   * The enriched path used at runtime: same segmentation and dedup as diagnoseAll, plus enrichers
   * (only when a context is given) and an LLM fallback. The LLM fires at most ONCE per call and only
   * when no rule card emerged from any block. This is a deliberate divergence from Java, which calls
   * the LLM per block/phase; firing once avoids a burst of slow Ollama calls on every poll delta.
   * With no context, no enrichers, and no llm this returns exactly what diagnoseAll returns.
   */
  async diagnoseAllEnriched(output: string, opts: EnrichOptions = {}): Promise<DiagnosisCard[]> {
    const enrichers = opts.enrichers ?? [];
    const context = opts.context ?? null;
    const byKey = new Map<string, DiagnosisCard>();
    for (const block of segment(output)) {
      const card = await this.diagnoseBlockEnriched(block, context, enrichers);
      if (card !== null) {
        const k = groupingKey(card);
        if (!byKey.has(k)) byKey.set(k, card);
      }
    }
    const cards = Array.from(byKey.values());
    if (cards.length === 0 && opts.llm) {
      const card = await opts.llm.diagnose(extract(output, 'STARTUP'));
      if (card !== null) return [card];
    }
    return cards;
  }

  private async diagnoseBlockEnriched(
    output: string | null,
    context: EnrichmentContext | null,
    enrichers: Enricher[],
  ): Promise<DiagnosisCard | null> {
    if (output === null || output.trim() === '') return null;

    const startupSig = extract(output, 'STARTUP');
    const startup = this.classifier.classify(startupSig);
    if (startup !== null) {
      return context ? this.runEnrichers(startup, startupSig, context, enrichers) : startup;
    }

    const compile = this.buildAnalyzer.analyze(output);
    if (compile !== null) return compile;

    const runtimeSig = extract(output, 'RUNTIME');
    const runtime = this.classifier.classify(runtimeSig);
    if (runtime !== null) {
      return context ? this.runEnrichers(runtime, runtimeSig, context, enrichers) : runtime;
    }

    return null;
  }

  private async runEnrichers(
    card: DiagnosisCard,
    signal: import('./models').RawSignal,
    context: EnrichmentContext,
    enrichers: Enricher[],
  ): Promise<DiagnosisCard> {
    let current = card;
    for (const enricher of enrichers) {
      try {
        current = await enricher.enrich(current, signal, context);
      } catch {
        // An enricher must never break the offline result; keep the current card.
      }
    }
    return current;
  }

  private diagnoseBlock(output: string | null): DiagnosisCard | null {
    if (output === null || output.trim() === '') return null;

    const startup = this.classifier.classify(extract(output, 'STARTUP'));
    if (startup !== null) return startup;

    const compile = this.buildAnalyzer.analyze(output);
    if (compile !== null) return compile;

    return this.classifier.classify(extract(output, 'RUNTIME'));
  }
}
