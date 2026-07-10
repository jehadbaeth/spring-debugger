// VS Code extension entry point. Thin glue: it loads the shared rule catalog, runs the ported
// ConsoleDiagnoser, and surfaces results through a webview card and a history tree. All diagnosis
// logic lives in the engine; this file only bridges to the vscode API.
import * as path from 'path';
import * as vscode from 'vscode';
import {
  ActuatorEnricher,
  ConsoleDiagnoser,
  DiagnosisCard,
  Enricher,
  EnrichmentContext,
  LlmDiagnosisEngine,
  OllamaHttpClient,
  PropertyPrecedenceEnricher,
  PsiEnricher,
  discoverAll,
} from './engine';
import { DiagnosisHistory } from './history';
import { filterByConfidence } from './confidence';
import { readSettings, Settings } from './settings';
import { TestResultsWatcher } from './capture/test-results-watch';
import { LogTailWatcher } from './capture/log-tail-watch';
import { loadBundledCatalog, loadBundledConventions } from './runtime/catalog';
import { VscodeEnrichmentContext } from './runtime/vscode-enrichment-context';
import { renderCardsHtml } from './ui/card-html';
import { HistoryTreeProvider } from './ui/history-tree';
import { ConventionCatalog } from './convention/convention-catalog';
import { ConventionRule } from './convention/convention-rule';
import { ConventionFinding, runConventions } from './convention/convention-engine';

let diagnoser: ConsoleDiagnoser | undefined;
let history: DiagnosisHistory | undefined;
let watcher: TestResultsWatcher | undefined;
let tailer: LogTailWatcher | undefined;
let panel: vscode.WebviewPanel | undefined;
let status: vscode.StatusBarItem | undefined;
let ruleCount = 0;
let activeContext: vscode.ExtensionContext | undefined;
let conventionsCatalog: ConventionCatalog | undefined;
let conventionDiagnostics: vscode.DiagnosticCollection | undefined;

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

  try {
    conventionsCatalog = loadBundledConventions(context.extensionPath);
  } catch (err) {
    vscode.window.showErrorMessage(
      'Spring Boot Debugger: failed to load the convention catalog. ' + String(err),
    );
  }

  activeContext = context;
  history = new DiagnosisHistory(readSettings().maxHistory);
  const tree = new HistoryTreeProvider(history);

  status = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 0);
  status.command = 'workbench.view.extension.springDebugger';

  watcher = new TestResultsWatcher((text) => diagnose(text), () => workspaceBase());
  tailer = new LogTailWatcher((text) => diagnose(text), () => resolveLogFiles());

  conventionDiagnostics = vscode.languages.createDiagnosticCollection('springDebuggerConventions');

  context.subscriptions.push(
    vscode.window.registerTreeDataProvider('springDebuggerHistory', tree),
    status,
    conventionDiagnostics,
    vscode.commands.registerCommand('springDebugger.diagnosePasted', () => diagnosePasted(context)),
    vscode.commands.registerCommand('springDebugger.openCard', (card: DiagnosisCard) =>
      showCards(context, [card]),
    ),
    vscode.commands.registerCommand('springDebugger.clearHistory', () => history?.clear()),
    vscode.workspace.onDidOpenTextDocument((doc) => refreshConventions(doc)),
    vscode.workspace.onDidChangeTextDocument((e) => refreshConventions(e.document)),
    vscode.workspace.onDidCloseTextDocument((doc) => conventionDiagnostics?.delete(doc.uri)),
    vscode.workspace.onDidChangeConfiguration((e) => {
      if (e.affectsConfiguration('springDebugger')) {
        applyConfig(context);
        refreshAllConventions();
      }
    }),
    { dispose: () => stopCapture() },
  );

  applyConfig(context);
  refreshAllConventions();
}

export function deactivate(): void {
  stopCapture();
  panel?.dispose();
  panel = undefined;
}

/** True if `rule` should run: a per-rule setting override wins, otherwise the catalog default. */
function isConventionRuleEnabled(rule: ConventionRule, s: Settings): boolean {
  const override = s.conventionRuleOverrides[rule.id];
  return override === undefined ? rule.enabled : override;
}

/** Runs every applicable convention rule against one document and publishes its diagnostics. */
function refreshConventions(doc: vscode.TextDocument): void {
  if (!conventionDiagnostics) return;
  if (!conventionsCatalog) return;

  const s = readSettings();
  if (!s.conventionsEnabled) {
    conventionDiagnostics.delete(doc.uri);
    return;
  }

  const ext = doc.fileName.slice(doc.fileName.lastIndexOf('.') + 1).toLowerCase();
  if (ext !== 'java' && ext !== 'robot') return;

  const text = doc.getText();
  const findings = runConventions(text, doc.fileName, conventionsCatalog, (rule) =>
    isConventionRuleEnabled(rule, s),
  );
  conventionDiagnostics.set(doc.uri, findings.map((f) => toDiagnostic(doc, f)));
}

function refreshAllConventions(): void {
  for (const doc of vscode.workspace.textDocuments) refreshConventions(doc);
}

function toDiagnostic(doc: vscode.TextDocument, finding: ConventionFinding): vscode.Diagnostic {
  const range = new vscode.Range(doc.positionAt(finding.range.start), doc.positionAt(finding.range.end));
  const diagnostic = new vscode.Diagnostic(range, finding.message, severityOf(finding.severity));
  diagnostic.source = 'Spring Boot Debugger';
  diagnostic.code = finding.ruleId;
  return diagnostic;
}

function severityOf(severity: string): vscode.DiagnosticSeverity {
  switch (severity) {
    case 'ERROR':
      return vscode.DiagnosticSeverity.Error;
    case 'WEAK_WARNING':
      return vscode.DiagnosticSeverity.Information;
    default:
      return vscode.DiagnosticSeverity.Warning;
  }
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

/**
 * The single diagnose entry point used by every path (paste and both watchers). It assembles the
 * enrichment layers from current settings: PSI source enrichment (default on, local and cheap),
 * Actuator + property-precedence enrichment (default off, probes the running app), and the Ollama
 * LLM fallback (default off). With all off this is exactly the rule-only engine.
 */
async function diagnose(text: string): Promise<DiagnosisCard[]> {
  if (!diagnoser) return [];
  const s = readSettings();

  const enrichers: Enricher[] = [];
  let context: EnrichmentContext | undefined;
  if (s.enrichSource || s.actuatorEnabled) {
    context = new VscodeEnrichmentContext(s.actuatorBaseUrl);
    if (s.enrichSource) enrichers.push(new PsiEnricher());
    if (s.actuatorEnabled) {
      enrichers.push(new ActuatorEnricher(), new PropertyPrecedenceEnricher());
    }
  }

  const llm = s.ollamaEnabled
    ? new LlmDiagnosisEngine(new OllamaHttpClient(s.ollamaBaseUrl, s.ollamaModel))
    : undefined;

  return diagnoser.diagnoseAllEnriched(text, { context, enrichers, llm });
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
  const cards = await diagnose(text);
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

/**
 * Test seam: drives the background-capture handler (the path a watcher callback takes) with the
 * active context, so the filter/history/notification wiring can be exercised without a real timer.
 */
export function __simulateCapturedCardsForTest(cards: DiagnosisCard[]): void {
  if (activeContext) onCapturedCards(activeContext, cards);
}
