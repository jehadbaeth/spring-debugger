// Port of com.springdebugger.tap.ConsoleDiagnoser (the shipped diagnoseAll entry point both
// products use). Segments a noisy buffer, diagnoses each block through STARTUP -> COMPILE ->
// RUNTIME, and de-duplicates by grouping key preserving first-occurrence order. This is the path
// the parity golden was generated from, so it must match the Java behaviour card for card.
import { BuildOutputAnalyzer } from './build-output-analyzer';
import { extract } from './log-extractor';
import { DiagnosisCard, groupingKey } from './models';
import { RuleBasedClassifier } from './rule-based-classifier';
import { RuleCatalog } from './rule-catalog';
import { segment } from './stack-trace-segmenter';

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

  private diagnoseBlock(output: string | null): DiagnosisCard | null {
    if (output === null || output.trim() === '') return null;

    const startup = this.classifier.classify(extract(output, 'STARTUP'));
    if (startup !== null) return startup;

    const compile = this.buildAnalyzer.analyze(output);
    if (compile !== null) return compile;

    return this.classifier.classify(extract(output, 'RUNTIME'));
  }
}
