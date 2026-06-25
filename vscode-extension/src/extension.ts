// VS Code extension entry point. Thin glue: it loads the shared rule catalog, runs the ported
// ConsoleDiagnoser, and surfaces results through a webview card and a history tree. All diagnosis
// logic lives in the engine; this file only bridges to the vscode API.
import * as path from 'path';
import * as vscode from 'vscode';
import { ConsoleDiagnoser, DiagnosisCard, discoverAll } from './engine';
import { DiagnosisHistory } from './history';
import { filterByConfidence } from './confidence';
import { readSettings } from './settings';
import { TestResultsWatcher } from './capture/test-results-watch';
import { LogTailWatcher } from './capture/log-tail-watch';
import { loadBundledCatalog } from './runtime/catalog';
import { renderCardsHtml } from './ui/card-html';
import { HistoryTreeProvider } from './ui/history-tree';

let diagnoser: ConsoleDiagnoser | undefined;
let history: DiagnosisHistory | undefined;
let watcher: TestResultsWatcher | undefined;
let tailer: LogTailWatcher | undefined;
let panel: vscode.WebviewPanel | undefined;
let status: vscode.StatusBarItem | undefined;
let ruleCount = 0;

export function activate(context: vscode.ExtensionContext): void {
  try {
    const catalog = loadBundledCatalog(context.extensionPath);
    ruleCount = catalog.activeCount();
    diagnoser = new ConsoleDiagnoser(catalog);
  } catch (err) {
    vscode.window.showErrorMessage(
      'Spring Boot Debugger: failed to load the rule catalog. ' + String(err),
    );
    return;
  }

  history = new DiagnosisHistory(readSettings().maxHistory);
  const tree = new HistoryTreeProvider(history);

  status = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 0);
  status.command = 'workbench.view.extension.springDebugger';

  watcher = new TestResultsWatcher(diagnoser, () => workspaceBase());
  tailer = new LogTailWatcher(diagnoser, () => resolveLogFiles());

  context.subscriptions.push(
    vscode.window.registerTreeDataProvider('springDebuggerHistory', tree),
    status,
    vscode.commands.registerCommand('springDebugger.diagnosePasted', () => diagnosePasted(context)),
    vscode.commands.registerCommand('springDebugger.openCard', (card: DiagnosisCard) =>
      showCards(context, [card]),
    ),
    vscode.commands.registerCommand('springDebugger.clearHistory', () => history?.clear()),
    vscode.workspace.onDidChangeConfiguration((e) => {
      if (e.affectsConfiguration('springDebugger')) applyConfig(context);
    }),
    { dispose: () => stopCapture() },
  );

  applyConfig(context);
}

export function deactivate(): void {
  stopCapture();
  panel?.dispose();
  panel = undefined;
}

/** (Re)applies all settings: history cap, capture gating, and the status bar. */
function applyConfig(context: vscode.ExtensionContext): void {
  const s = readSettings();
  history?.setMaxEntries(s.maxHistory);

  stopCapture();
  if (s.enabled && s.watchTestResults) {
    watcher?.start((cards) => onCapturedCards(context, cards));
  }
  if (s.enabled && s.watchLogFile) {
    tailer?.start((cards) => onCapturedCards(context, cards));
  }

  if (!status) return;
  if (!s.enabled) {
    status.hide();
    return;
  }
  const paths: string[] = [];
  if (s.watchTestResults) paths.push('tests');
  if (s.watchLogFile) paths.push('logs');
  status.text = '$(beaker) Spring Debugger';
  status.tooltip = `${ruleCount} rules · watching: ${paths.length ? paths.join(', ') : 'paste only'}`;
  status.show();
}

function stopCapture(): void {
  watcher?.stop();
  tailer?.stop();
}

function workspaceBase(): string | undefined {
  return vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
}

/** An explicit logFilePath setting wins; otherwise auto-discover every logging.file.name. */
function resolveLogFiles(): string[] {
  const base = workspaceBase();
  if (!base) return [];
  const configured = readSettings().logFilePath;
  if (configured !== '') {
    return [path.isAbsolute(configured) ? configured : path.join(base, configured)];
  }
  return discoverAll(base);
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

  // Explicit paste shows everything found, regardless of the minimumConfidence threshold that
  // governs background capture: the user asked for a diagnosis of this specific text.
  const cards = diagnoser.diagnoseAll(text);
  for (const card of cards) history?.add(card);
  showCards(context, cards);
  if (cards.length === 0) {
    vscode.window.showInformationMessage(
      'Spring Boot Debugger: no known error recognised in the pasted output.',
    );
  }
}

/** Handles cards produced by a background capture path (test results, log tailing). */
function onCapturedCards(context: vscode.ExtensionContext, cards: DiagnosisCard[]): void {
  const s = readSettings();
  const surfaced = filterByConfidence(cards, s.minimumConfidence);
  if (surfaced.length === 0) return;
  for (const card of surfaced) history?.add(card);
  if (!s.showNotifications) return;
  const first = surfaced[0];
  vscode.window
    .showWarningMessage(`Spring Boot Debugger: ${first.ruleId} — ${first.diagnosisSentence}`, 'Show')
    .then((sel) => {
      if (sel === 'Show') showCards(context, surfaced);
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
