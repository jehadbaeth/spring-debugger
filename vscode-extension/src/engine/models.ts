// Port of com.springdebugger.model.*  Kept structurally identical to the Java models so the
// parity suite can compare card-for-card. Enums are string unions matching Java enum .name().

export type Phase = 'COMPILE' | 'STARTUP' | 'RUNTIME' | 'TEST';

export type Confidence = 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE';

/** The final output of the classification pipeline (port of DiagnosisCard). */
export interface DiagnosisCard {
  ruleId: string;
  phase: Phase | null;
  diagnosisSentence: string;
  fixSentence: string;
  confidence: Confidence;
  excerpt: string;
}

/** Stable identity for de-duplication and history grouping (same rule + same diagnosis). */
export function groupingKey(card: DiagnosisCard): string {
  return card.ruleId + '|' + card.diagnosisSentence;
}

/**
 * Structured data extracted from a raw log or build output stream before classification.
 * Port of com.springdebugger.model.RawSignal.
 */
export class RawSignal {
  constructor(
    readonly phase: Phase,
    readonly deepestCausedByClass: string | null,
    readonly deepestCausedByMessage: string | null,
    readonly bannerDescription: string | null,
    readonly bannerAction: string | null,
    readonly failingBeanName: string | null,
    readonly httpStatus: number,
    readonly relevantLines: readonly string[],
    readonly rawExcerpt: string,
  ) {}

  /** True if any relevant line contains the given substring (case-insensitive). */
  anyLineContains(substring: string): boolean {
    const lower = substring.toLowerCase();
    return this.relevantLines.some((l) => l.toLowerCase().includes(lower));
  }
}
