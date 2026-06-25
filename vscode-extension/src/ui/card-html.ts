// Pure rendering of diagnosis cards to webview HTML. Kept free of the vscode API so it is unit
// tested directly. The two-sentence card mirrors the IntelliJ DiagnosisCardPanel: one line names
// the problem, one gives the fix, with the rule id, phase, and confidence as metadata.
import { DiagnosisCard } from '../engine';

export function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function confidenceClass(confidence: string): string {
  switch (confidence) {
    case 'HIGH':
      return 'conf-high';
    case 'MEDIUM':
      return 'conf-medium';
    default:
      return 'conf-low';
  }
}

function cardSection(card: DiagnosisCard, index: number): string {
  const id = escapeHtml(card.ruleId);
  const phase = escapeHtml(card.phase ?? '');
  const conf = escapeHtml(card.confidence);
  return `
    <section class="card">
      <header>
        <span class="rule-id">${id}</span>
        <span class="badge ${confidenceClass(card.confidence)}">${conf}</span>
        <span class="phase">${phase}</span>
      </header>
      <p class="diagnosis">${escapeHtml(card.diagnosisSentence)}</p>
      <p class="fix"><strong>Fix:</strong> ${escapeHtml(card.fixSentence)}</p>
      <div class="actions">
        <button data-copy="diagnosis" data-index="${index}">Copy diagnosis</button>
        <button data-copy="fix" data-index="${index}">Copy fix</button>
      </div>
    </section>`;
}

function emptyState(): string {
  return `<p class="empty">No known Spring Boot error was recognised in this output.</p>`;
}

/**
 * Renders the full webview document for a set of cards. Self-contained: inline CSS and JS only,
 * gated by a CSP that only trusts the given nonce for scripts. The nonce is optional so the body
 * can be unit tested without the vscode webview.
 */
export function renderCardsHtml(cards: DiagnosisCard[], nonce = ''): string {
  const body = cards.length === 0 ? emptyState() : cards.map(cardSection).join('\n');
  // Escape < so the JSON embedded in the inline <script> can never form a </script> or an HTML
  // tag, even when card text contains markup. This is the script-context complement to escapeHtml.
  const payload = JSON.stringify(
    cards.map((c) => ({ diagnosis: c.diagnosisSentence, fix: c.fixSentence })),
  ).replace(/</g, '\\u003c');
  const csp =
    `default-src 'none'; style-src 'unsafe-inline'; ` +
    `script-src 'nonce-${nonce}';`;
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta http-equiv="Content-Security-Policy" content="${csp}">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Spring Boot Debugger</title>
  <style>
    body { font-family: var(--vscode-font-family); color: var(--vscode-foreground); padding: 0.5rem 1rem; }
    .card { border: 1px solid var(--vscode-panel-border); border-radius: 6px; padding: 0.75rem 1rem; margin-bottom: 0.75rem; }
    header { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem; }
    .rule-id { font-weight: 600; }
    .badge { font-size: 0.7rem; padding: 0.1rem 0.4rem; border-radius: 3px; }
    .conf-high { background: var(--vscode-testing-iconPassed, #2e7d32); color: #fff; }
    .conf-medium { background: var(--vscode-editorWarning-foreground, #b58900); color: #000; }
    .conf-low { background: var(--vscode-descriptionForeground, #888); color: #fff; }
    .phase { opacity: 0.7; font-size: 0.75rem; }
    .diagnosis { margin: 0.25rem 0; }
    .fix { margin: 0.25rem 0; opacity: 0.95; }
    .actions { margin-top: 0.5rem; display: flex; gap: 0.5rem; }
    button { background: var(--vscode-button-background); color: var(--vscode-button-foreground); border: none; padding: 0.25rem 0.6rem; border-radius: 3px; cursor: pointer; }
    button:hover { background: var(--vscode-button-hoverBackground); }
    .empty { opacity: 0.7; }
  </style>
</head>
<body>
  <main>${body}</main>
  <script nonce="${nonce}">
    const vscode = acquireVsCodeApi();
    const cards = ${payload};
    document.querySelectorAll('button[data-copy]').forEach((b) => {
      b.addEventListener('click', () => {
        const i = Number(b.getAttribute('data-index'));
        const which = b.getAttribute('data-copy');
        const text = which === 'fix' ? cards[i].fix : cards[i].diagnosis;
        vscode.postMessage({ type: 'copy', text });
      });
    });
  </script>
</body>
</html>`;
}
