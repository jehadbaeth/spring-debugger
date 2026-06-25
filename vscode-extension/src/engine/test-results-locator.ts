// Port of com.springdebugger.extractor.TestResultsLocator. Finds JUnit result XML under a project
// tree (Gradle build/test-results, Maven surefire/failsafe reports) with a bounded, pruned walk so
// it stays cheap on large monorepos. Returns absolute file paths.
import * as fs from 'fs';
import * as path from 'path';

const MAX_DEPTH = 8;

export function locate(projectBase: string): string[] {
  const results: string[] = [];
  if (!isDirectory(projectBase)) return results;
  walk(projectBase, 0, results);
  return results;
}

function walk(dir: string, depth: number, out: string[]): void {
  if (depth > MAX_DEPTH) return;
  for (const child of listDirs(dir)) {
    const name = path.basename(child);
    if (isPruned(name)) continue;
    if (isResultDir(child)) {
      collectXml(child, out);
      continue; // result dirs do not nest result dirs
    }
    walk(child, depth + 1, out);
  }
}

function isResultDir(dir: string): boolean {
  const p = dir.split(path.sep).join('/');
  return (
    p.includes('/build/test-results/') ||
    p.endsWith('/surefire-reports') ||
    p.endsWith('/failsafe-reports')
  );
}

function isPruned(name: string): boolean {
  switch (name) {
    case '.git':
    case '.gradle':
    case '.idea':
    case 'node_modules':
    case 'src':
    case '.m2':
      return true;
    default:
      return name.startsWith('.') && name !== '.';
  }
}

function collectXml(dir: string, out: string[]): void {
  let entries: fs.Dirent[];
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return;
  }
  for (const e of entries) {
    const full = path.join(dir, e.name);
    if (e.isFile() && e.name.endsWith('.xml')) {
      out.push(full);
    } else if (e.isDirectory()) {
      collectXml(full, out); // Gradle nests per-suite XML one level down
    }
  }
}

function listDirs(dir: string): string[] {
  try {
    return fs
      .readdirSync(dir, { withFileTypes: true })
      .filter((e) => e.isDirectory())
      .map((e) => path.join(dir, e.name));
  } catch {
    return [];
  }
}

function isDirectory(p: string): boolean {
  try {
    return fs.statSync(p).isDirectory();
  } catch {
    return false;
  }
}
