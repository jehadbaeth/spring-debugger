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
  enrichSource: boolean;
  actuatorEnabled: boolean;
  actuatorBaseUrl: string;
  ollamaEnabled: boolean;
  ollamaBaseUrl: string;
  ollamaModel: string;
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
    enrichSource: c.get<boolean>('enrichSource', true),
    actuatorEnabled: c.get<boolean>('actuator.enabled', false),
    actuatorBaseUrl: c.get<string>('actuator.baseUrl', 'http://localhost:8080').trim(),
    ollamaEnabled: c.get<boolean>('ollama.enabled', false),
    ollamaBaseUrl: c.get<string>('ollama.baseUrl', 'http://localhost:11434').trim(),
    ollamaModel: c.get<string>('ollama.model', 'llama3.2').trim(),
  };
}
