// TreeView of the diagnosis history (newest first, with occurrence counts), the VS Code analogue of
// the IntelliJ tool-window history list. Selecting an item opens its card. The data model lives in
// DiagnosisHistory; this is the thin vscode binding.
import * as vscode from 'vscode';
import { DiagnosisHistory, HistoryEntry } from '../history';

export class HistoryTreeProvider implements vscode.TreeDataProvider<HistoryEntry> {
  private readonly changed = new vscode.EventEmitter<void>();
  readonly onDidChangeTreeData = this.changed.event;

  constructor(private readonly history: DiagnosisHistory) {
    this.history.onChange(() => this.changed.fire());
  }

  getTreeItem(entry: HistoryEntry): vscode.TreeItem {
    const card = entry.card;
    const item = new vscode.TreeItem(
      `${card.ruleId}  ${truncate(card.diagnosisSentence, 70)}`,
      vscode.TreeItemCollapsibleState.None,
    );
    item.description = entry.count > 1 ? `${card.confidence} ×${entry.count}` : card.confidence;
    const tip = new vscode.MarkdownString();
    tip.appendMarkdown(`**${card.ruleId}** · ${card.phase ?? ''} · ${card.confidence}\n\n`);
    tip.appendMarkdown(`${card.diagnosisSentence}\n\n**Fix:** ${card.fixSentence}`);
    item.tooltip = tip;
    item.iconPath = new vscode.ThemeIcon(iconFor(card.confidence));
    item.command = {
      command: 'springDebugger.openCard',
      title: 'Open diagnosis card',
      arguments: [card],
    };
    return item;
  }

  getChildren(element?: HistoryEntry): HistoryEntry[] {
    return element ? [] : this.history.entries();
  }
}

function truncate(s: string, max: number): string {
  return s.length <= max ? s : s.slice(0, max - 1) + '…';
}

function iconFor(confidence: string): string {
  switch (confidence) {
    case 'HIGH':
      return 'error';
    case 'MEDIUM':
      return 'warning';
    default:
      return 'info';
  }
}
