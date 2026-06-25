import { describe, it, expect } from 'vitest';
import { renderCardsHtml, escapeHtml } from '../src/ui/card-html';
import { DiagnosisCard } from '../src/engine';

const card: DiagnosisCard = {
  ruleId: '4.15',
  phase: 'STARTUP',
  diagnosisSentence: 'The database is unreachable.',
  fixSentence: 'Start the database & retry.',
  confidence: 'HIGH',
  excerpt: 'Connection refused',
};

describe('renderCardsHtml', () => {
  it('renders a full document with CSP nonce', () => {
    const html = renderCardsHtml([card], 'abc123');
    expect(html).toContain('<!DOCTYPE html>');
    expect(html).toContain("script-src 'nonce-abc123'");
    expect(html).toContain('<script nonce="abc123">');
  });

  it('includes the diagnosis and fix text', () => {
    const html = renderCardsHtml([card]);
    expect(html).toContain('The database is unreachable.');
    expect(html).toContain('Start the database &amp; retry.');
    expect(html).toContain('4.15');
    expect(html).toContain('STARTUP');
  });

  it('escapes HTML in card content', () => {
    const xss: DiagnosisCard = { ...card, diagnosisSentence: '<img src=x onerror=alert(1)>' };
    const html = renderCardsHtml([xss]);
    expect(html).not.toContain('<img src=x');
    expect(html).toContain('&lt;img src=x');
  });

  it('shows an empty state when there are no cards', () => {
    const html = renderCardsHtml([]);
    expect(html).toContain('No known Spring Boot error');
  });
});

describe('escapeHtml', () => {
  it('escapes the five significant characters', () => {
    expect(escapeHtml(`<>&"'`)).toBe('&lt;&gt;&amp;&quot;&#39;');
  });
});
