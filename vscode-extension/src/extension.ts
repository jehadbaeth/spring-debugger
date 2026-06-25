// VS Code extension entry point. Thin glue: it loads the shared rule catalog, runs the ported
// ConsoleDiagnoser, and renders cards in a reused webview. All diagnosis logic lives in the engine;
// this file only bridges to the vscode API.
import * as vscode from 'vscode';
import { ConsoleDiagnoser, DiagnosisCard } from './engine';
import { loadBundledCatalog } from './runtime/catalog';
import { renderCardsHtml } from './ui/card-html';

let diagnoser: ConsoleDiagnoser | undefined;
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

  context.subscriptions.push(
    vscode.commands.registerCommand('springDebugger.diagnosePasted', () =>
      diagnosePasted(context),
    ),
  );
}

export function deactivate(): void {
  panel?.dispose();
  panel = undefined;
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
  if (!text || text.trim() === '') {
    return;
  }

  const cards = diagnoser.diagnoseAll(text);
  showCards(context, cards);
  if (cards.length === 0) {
    vscode.window.showInformationMessage(
      'Spring Boot Debugger: no known error recognised in the pasted output.',
    );
  }
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
