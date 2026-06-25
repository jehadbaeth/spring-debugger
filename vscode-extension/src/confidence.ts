// Confidence threshold filtering, mirroring the IntelliJ minimumConfidence setting. Pure and
// unit tested. NONE never passes a threshold (it means no rule fired).
import { Confidence, DiagnosisCard } from './engine';

const RANK: Record<Confidence, number> = { HIGH: 3, MEDIUM: 2, LOW: 1, NONE: 0 };

export function meetsThreshold(confidence: Confidence, minimum: Confidence): boolean {
  return RANK[confidence] >= RANK[minimum] && RANK[confidence] > 0;
}

export function filterByConfidence(cards: DiagnosisCard[], minimum: Confidence): DiagnosisCard[] {
  return cards.filter((c) => meetsThreshold(c.confidence, minimum));
}
