// VS Code extension entry point. Thin glue: it loads the shared rule catalog, runs the ported
// ConsoleDiagnoser, and surfaces results through a webview card and a history tree. All diagnosis
// logic lives in the engine; this file only bridges to the vscode API.
import * as vscode from 'vscode';
import { ConsoleDiagnoser, DiagnosisCard } from './engine';
import { DiagnosisHistory } from './history';
import { TestResultsWatcher } from './capture/test-results-watch';
import { loadBundledCatalog } from './runtime/catalog';
import { renderCardsHtml } from './ui/card-html';
import { HistoryTreeProvider } from './ui/history-tree';

let diagnoser: ConsoleDiagnoser | undefined;
let history: DiagnosisHistory | undefined;
let watcher: TestResultsWatcher | undefined;
let panel: vscode.WebviewPanel | undefined;

export function activate(context: vscode.ExtensionContext): void {
  try {
    diagnoser = new ConsoleDiagnoser(loadBundledCatalog(context.extensionPath));
  } catch (err) {
    vscode.window.showErrorMessage(
      'Spring Boot Debugger: failed to load the rule catalog. ' + String(err),
    );
    return;
  }

  history = new DiagnosisHistory();
  const tree = new HistoryTreeProvider(history);
  context.subscriptions.push(
    vscode.window.registerTreeDataProvider('springDebuggerHistory', tree),
    vscode.commands.registerCommand('springDebugger.diagnosePasted', () => diagnosePasted(context)),
    vscode.commands.registerCommand('springDebugger.openCard', (card: DiagnosisCard) =>
      showCards(context, [card]),
    ),
    vscode.commands.registerCommand('springDebugger.clearHistory', () => history?.clear()),
  );

  watcher = new TestResultsWatcher(diagnoser, () => workspaceBase());
  watcher.start((cards) => onCapturedCards(context, cards));
  context.subscriptions.push({ dispose: () => watcher?.stop() });
}

export function deactivate(): void {
  watcher?.stop();
  panel?.dispose();
  panel = undefined;
}

function workspaceBase(): string | undefined {
  return vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
}

async function diagnosePasted(context: vscode.ExtensionContext): Promise<void> {
  if (!diagnoser) return;

  // Prefer the clipboard so multi-line stack traces come through intact; fall back to an input box.
  let text = await vscode.env.clipboard.readText();
  if (!text || text.trim() === '') {
    text =
      (await vscode.window.showInputBox({
        prompt: 'Paste Spring Boot output to diagnose',
        placeHolder: 'Exception text, log lines, or a stack trace',
      })) ?? '';
  }
  if (!text || text.trim() === '') return;

  const cards = diagnoser.diagnoseAll(text);
  for (const card of cards) history?.add(card);
  showCards(context, cards);
  if (cards.length === 0) {
    vscode.window.showInformationMessage(
      'Spring Boot Debugger: no known error recognised in the pasted output.',
    );
  }
}

/** Handles cards produced by a background capture path (test results, later log tailing). */
function onCapturedCards(context: vscode.ExtensionContext, cards: DiagnosisCard[]): void {
  if (cards.length === 0) return;
  for (const card of cards) history?.add(card);
  const first = cards[0];
  vscode.window
    .showWarningMessage(`Spring Boot Debugger: ${first.ruleId} — ${first.diagnosisSentence}`, 'Show')
    .then((sel) => {
      if (sel === 'Show') showCards(context, cards);
    });
}

function showCards(context: vscode.ExtensionContext, cards: DiagnosisCard[]): void {
  if (!panel) {
    panel = vscode.window.createWebviewPanel(
      'springDebuggerCard',
      'Spring Boot Debugger',
      vscode.ViewColumn.Beside,
      { enableScripts: true, retainContextWhenHidden: true },
    );
    panel.onDidDispose(() => {
      panel = undefined;
    });
    panel.webview.onDidReceiveMessage(
      (msg: { type?: string; text?: string }) => {
        if (msg?.type === 'copy' && typeof msg.text === 'string') {
          vscode.env.clipboard.writeText(msg.text);
          vscode.window.setStatusBarMessage('Spring Boot Debugger: copied to clipboard', 2000);
        }
      },
      undefined,
      context.subscriptions,
    );
  }
  panel.webview.html = renderCardsHtml(cards, nonce());
  panel.reveal(vscode.ViewColumn.Beside, true);
}

function nonce(): string {
  let s = '';
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  for (let i = 0; i < 32; i++) s += chars.charAt(Math.floor(Math.random() * chars.length));
  return s;
}
