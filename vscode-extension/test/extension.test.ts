import { afterEach, beforeEach, describe, it, expect } from 'vitest';
import * as ext from '../src/extension';
import { control } from './vscode-stub';
import { DiagnosisCard } from '../src/engine';
import { HistoryTreeProvider } from '../src/ui/history-tree';
import { readCorpusFile } from './helpers';

// Runs the extension glue (activate, commands, config wiring, webview, tree, status) against the
// controllable vscode stub. This is the layer the pure module tests never exercised.

function fakeContext() {
  return { extensionPath: process.cwd(), subscriptions: [] as { dispose(): void }[] } as never;
}

function tree(): HistoryTreeProvider {
  return control.treeProviders.get('springDebuggerHistory') as HistoryTreeProvider;
}

function syntheticCard(confidence: DiagnosisCard['confidence'], ruleId = 'X'): DiagnosisCard {
  return { ruleId, phase: 'STARTUP', diagnosisSentence: `${ruleId} diag`, fixSentence: 'fix', confidence, excerpt: 'e' };
}

describe('extension activation and glue', () => {
  beforeEach(() => {
    control.reset();
    ext.activate(fakeContext());
  });

  afterEach(() => {
    ext.deactivate();
  });

  it('registers the three commands', () => {
    expect(control.commands.has('springDebugger.diagnosePasted')).toBe(true);
    expect(control.commands.has('springDebugger.openCard')).toBe(true);
    expect(control.commands.has('springDebugger.clearHistory')).toBe(true);
  });

  it('shows a status bar with the rule count', () => {
    expect(control.statusBar?.shown).toBe(true);
    expect(control.statusBar?.text).toContain('Spring Debugger');
    expect(control.statusBar?.tooltip).toMatch(/\d+ rules/);
  });

  it('diagnoses pasted clipboard output into a webview and history', async () => {
    control.clipboard = readCorpusFile('real-world-logs/FG-SERVICE-gradle-multimodule-bootrun.log');
    await control.run('springDebugger.diagnosePasted');

    const html = control.panels.at(-1)?.html ?? '';
    for (const ruleId of ['4.15', '2.1', '14.1']) expect(html).toContain(ruleId);
    expect(control.panels.at(-1)?.revealed).toBe(true);
    expect(tree().getChildren().map((e) => e.card.ruleId).sort()).toEqual(['14.1', '2.1', '4.15']);
  });

  it('reports when pasted output has no known error', async () => {
    control.clipboard = 'just a normal log line, nothing wrong here';
    await control.run('springDebugger.diagnosePasted');
    expect(control.messages.some((m) => m.kind === 'info' && /no known error/i.test(m.text))).toBe(true);
  });

  it('opens a card for a history entry via openCard', async () => {
    await control.run('springDebugger.openCard', syntheticCard('HIGH', '9.9'));
    expect(control.panels.at(-1)?.html).toContain('9.9');
  });

  it('clears history', async () => {
    control.clipboard = readCorpusFile('real-world-logs/FG-SERVICE-gradle-multimodule-bootrun.log');
    await control.run('springDebugger.diagnosePasted');
    expect(tree().getChildren().length).toBeGreaterThan(0);
    await control.run('springDebugger.clearHistory');
    expect(tree().getChildren().length).toBe(0);
  });

  it('hides the status bar and stops capture when disabled', () => {
    control.configStore.enabled = false;
    control.fireConfigChange();
    expect(control.statusBar?.shown).toBe(false);
  });

  it('reflects capture paths in the status tooltip', () => {
    control.configStore.watchTestResults = false;
    control.configStore.watchLogFile = false;
    control.fireConfigChange();
    expect(control.statusBar?.tooltip).toContain('paste only');
  });

  describe('background capture', () => {
    it('filters below the confidence threshold and notifies for the first', () => {
      ext.__simulateCapturedCardsForTest([syntheticCard('LOW', 'low'), syntheticCard('HIGH', 'high')]);
      // Default minimumConfidence is MEDIUM: the LOW card is dropped, the HIGH card lands.
      expect(tree().getChildren().map((e) => e.card.ruleId)).toEqual(['high']);
      expect(control.messages.some((m) => m.kind === 'warning' && m.text.includes('high'))).toBe(true);
    });

    it('does not notify when notifications are off', () => {
      control.configStore.showNotifications = false;
      ext.__simulateCapturedCardsForTest([syntheticCard('HIGH', 'high')]);
      expect(control.messages.some((m) => m.kind === 'warning')).toBe(false);
      // The card is still recorded in history.
      expect(tree().getChildren().map((e) => e.card.ruleId)).toEqual(['high']);
    });
  });
});
