import { describe, it, expect } from 'vitest';
import { meetsThreshold, filterByConfidence } from '../src/confidence';
import { DiagnosisCard, Confidence } from '../src/engine';

function card(confidence: Confidence): DiagnosisCard {
  return { ruleId: 'x', phase: 'STARTUP', diagnosisSentence: 'd', fixSentence: 'f', confidence, excerpt: 'e' };
}

describe('meetsThreshold', () => {
  it('passes equal or higher confidence', () => {
    expect(meetsThreshold('HIGH', 'MEDIUM')).toBe(true);
    expect(meetsThreshold('MEDIUM', 'MEDIUM')).toBe(true);
    expect(meetsThreshold('LOW', 'MEDIUM')).toBe(false);
  });

  it('never passes NONE', () => {
    expect(meetsThreshold('NONE', 'LOW')).toBe(false);
  });
});

describe('filterByConfidence', () => {
  it('keeps only cards at or above the threshold', () => {
    const cards = [card('HIGH'), card('MEDIUM'), card('LOW')];
    expect(filterByConfidence(cards, 'MEDIUM').map((c) => c.confidence)).toEqual(['HIGH', 'MEDIUM']);
  });
});
