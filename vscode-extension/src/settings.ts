// Reads the springDebugger.* settings from the VS Code configuration. Thin wrapper so the rest of
// the extension works with a plain typed object.
import * as vscode from 'vscode';
import { Confidence } from './engine';

export interface Settings {
  enabled: boolean;
  minimumConfidence: Confidence;
  maxHistory: number;
  showNotifications: boolean;
  watchTestResults: boolean;
  watchLogFile: boolean;
  logFilePath: string;
}

export function readSettings(): Settings {
  const c = vscode.workspace.getConfiguration('springDebugger');
  return {
    enabled: c.get<boolean>('enabled', true),
    minimumConfidence: c.get<Confidence>('minimumConfidence', 'MEDIUM'),
    maxHistory: c.get<number>('maxHistory', 30),
    showNotifications: c.get<boolean>('showNotifications', true),
    watchTestResults: c.get<boolean>('watchTestResults', true),
    watchLogFile: c.get<boolean>('watchLogFile', true),
    logFilePath: c.get<string>('logFilePath', '').trim(),
  };
}
