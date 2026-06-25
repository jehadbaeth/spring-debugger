// VS Code implementation of EnrichmentContext. Resolves classes through the STABLE workspace symbol
// provider (vscode.executeWorkspaceSymbolProvider, backed by redhat.java when present), reads the
// located file into ClassFacts, and probes a running app over HTTP. It degrades to null/empty on any
// failure, so enrichment never breaks the offline result and works (minus PSI) without redhat.java.
//
// Correctness guard (per review): never produce a confidently-wrong upgrade. For a fully qualified
// name it requires an exact, unambiguous package match; for a bare simple name it requires exactly
// one candidate, otherwise it returns null rather than guess which package.
import * as path from 'path';
import * as vscode from 'vscode';
import { ClassFacts, EnrichmentContext } from '../engine';
import { buildClassFacts, packageOf } from './java-source-parse';

export class VscodeEnrichmentContext implements EnrichmentContext {
  private appPackages: string[] | undefined;

  constructor(private readonly actuatorBaseUrl: string) {}

  async findClass(name: string): Promise<ClassFacts | null> {
    const simple = name.includes('.') ? name.substring(name.lastIndexOf('.') + 1) : name;
    const expectedPkg = name.includes('.') ? name.substring(0, name.lastIndexOf('.')) : null;

    let symbols: vscode.SymbolInformation[];
    try {
      symbols =
        (await vscode.commands.executeCommand<vscode.SymbolInformation[]>(
          'vscode.executeWorkspaceSymbolProvider',
          simple,
        )) ?? [];
    } catch {
      return null;
    }

    const candidates = symbols.filter(
      (s) =>
        s.name === simple &&
        (s.kind === vscode.SymbolKind.Class || s.kind === vscode.SymbolKind.Interface),
    );
    if (candidates.length === 0) return null;

    let chosen: vscode.SymbolInformation | undefined;
    if (expectedPkg !== null) {
      const matches: vscode.SymbolInformation[] = [];
      for (const c of candidates) {
        if ((await packageOfUri(c.location.uri)) === expectedPkg) matches.push(c);
      }
      if (matches.length !== 1) return null; // FQN: require an exact, unambiguous package match
      chosen = matches[0];
    } else {
      if (candidates.length !== 1) return null; // bare name: bail on ambiguity
      chosen = candidates[0];
    }

    try {
      const doc = await vscode.workspace.openTextDocument(chosen.location.uri);
      return buildClassFacts(doc.getText(), simple, path.basename(chosen.location.uri.fsPath));
    } catch {
      return null;
    }
  }

  async springBootApplicationPackages(): Promise<string[]> {
    if (this.appPackages !== undefined) return this.appPackages;
    const out = new Set<string>();
    try {
      const files = await vscode.workspace.findFiles(
        '**/*.java',
        '**/{build,target,node_modules,.gradle}/**',
        400,
      );
      for (const uri of files) {
        try {
          const text = (await vscode.workspace.openTextDocument(uri)).getText();
          if (text.includes('@SpringBootApplication')) {
            const pkg = packageOf(text);
            if (pkg) out.add(pkg);
          }
        } catch {
          // skip an unreadable file
        }
      }
    } catch {
      // no symbol/file provider available
    }
    this.appPackages = Array.from(out);
    return this.appPackages;
  }

  async httpGet(urlPath: string): Promise<string | null> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 2000);
    try {
      const res = await fetch(this.actuatorBaseUrl.replace(/\/+$/, '') + urlPath, {
        signal: controller.signal,
      });
      if (Math.floor(res.status / 100) !== 2) return null;
      return await res.text();
    } catch {
      return null;
    } finally {
      clearTimeout(timer);
    }
  }
}

async function packageOfUri(uri: vscode.Uri): Promise<string | null> {
  try {
    return packageOf((await vscode.workspace.openTextDocument(uri)).getText());
  } catch {
    return null;
  }
}
