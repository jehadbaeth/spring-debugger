import { describe, it, expect } from 'vitest';
import { DiagnosisHistory } from '../src/history';
import { DiagnosisCard } from '../src/engine';

function card(ruleId: string, diagnosis = 'd'): DiagnosisCard {
  return { ruleId, phase: 'STARTUP', diagnosisSentence: diagnosis, fixSentence: 'f', confidence: 'HIGH', excerpt: 'x' };
}

describe('DiagnosisHistory', () => {
  it('adds new entries newest-first', () => {
    const h = new DiagnosisHistory();
    expect(h.add(card('2.1'))).toBe(true);
    expect(h.add(card('4.15'))).toBe(true);
    expect(h.entries().map((e) => e.card.ruleId)).toEqual(['4.15', '2.1']);
  });

  it('coalesces repeats by grouping key and counts them', () => {
    const h = new DiagnosisHistory();
    h.add(card('2.1'));
    expect(h.add(card('2.1'))).toBe(false);
    const entries = h.entries();
    expect(entries).toHaveLength(1);
    expect(entries[0].count).toBe(2);
  });

  it('moves a repeated entry back to the front', () => {
    const h = new DiagnosisHistory();
    h.add(card('2.1'));
    h.add(card('4.15'));
    h.add(card('2.1'));
    expect(h.entries().map((e) => e.card.ruleId)).toEqual(['2.1', '4.15']);
  });

  it('treats same rule with different diagnosis as distinct', () => {
    const h = new DiagnosisHistory();
    h.add(card('2.1', 'one'));
    h.add(card('2.1', 'two'));
    expect(h.entries()).toHaveLength(2);
  });

  it('caps at maxEntries', () => {
    const h = new DiagnosisHistory(2);
    h.add(card('1'));
    h.add(card('2'));
    h.add(card('3'));
    expect(h.entries().map((e) => e.card.ruleId)).toEqual(['3', '2']);
  });

  it('notifies listeners on change', () => {
    const h = new DiagnosisHistory();
    let calls = 0;
    h.onChange(() => calls++);
    h.add(card('2.1'));
    h.clear();
    expect(calls).toBe(2);
  });
});
