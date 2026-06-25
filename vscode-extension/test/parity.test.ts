import * as fs from 'fs';
import { describe, it, expect } from 'vitest';
import { ConsoleDiagnoser, DiagnosisCard } from '../src/engine';
import { GOLDEN_PATH, loadCanonicalCatalog, readCorpusFile } from './helpers';

// Cross-engine parity: the TypeScript engine must reproduce, card for card, what the shipped Java
// ConsoleDiagnoser produced over the whole corpus (see ParityGoldenTest). The golden is the single
// source of truth; if this fails after an intentional rule/engine change, regenerate the golden on
// the Java side and both must move together.

interface GoldenCard {
  ruleId: string;
  phase: string | null;
  diagnosis: string | null;
  fix: string | null;
  confidence: string | null;
  excerptNonEmpty: boolean;
}

type Golden = Record<string, GoldenCard[]>;

function toComparable(card: DiagnosisCard): GoldenCard {
  return {
    ruleId: card.ruleId,
    phase: card.phase,
    diagnosis: card.diagnosisSentence,
    fix: card.fixSentence,
    confidence: card.confidence,
    excerptNonEmpty: card.excerpt != null && card.excerpt.trim() !== '',
  };
}

describe('cross-engine parity with the Java golden', () => {
  const golden: Golden = JSON.parse(fs.readFileSync(GOLDEN_PATH, 'utf8'));
  const diagnoser = new ConsoleDiagnoser(loadCanonicalCatalog());

  const keys = Object.keys(golden);

  it('covers a non-trivial corpus', () => {
    expect(keys.length).toBeGreaterThan(80);
  });

  for (const key of keys) {
    it(`matches Java cards for ${key}`, () => {
      const produced = diagnoser.diagnoseAll(readCorpusFile(key)).map(toComparable);
      expect(produced).toEqual(golden[key]);
    });
  }
});
