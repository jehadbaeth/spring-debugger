import * as fs from 'fs';
import * as path from 'path';
import { RuleCatalog } from '../src/engine';
import { ConventionCatalog } from '../src/convention/convention-catalog';

// Tests run from the vscode-extension directory, so the repo root is one level up. The engine is
// validated against the SAME canonical files the Java engine and golden use, never a copy.
export const REPO_ROOT = path.resolve(process.cwd(), '..');

export const CANONICAL_RULES = path.join(
  REPO_ROOT,
  'src',
  'main',
  'resources',
  'rules',
  'spring-boot-rules.yaml',
);

export const CANONICAL_CONVENTIONS = path.join(
  REPO_ROOT,
  'src',
  'main',
  'resources',
  'rules',
  'conventions.yaml',
);

export const GOLDEN_PATH = path.join(REPO_ROOT, 'parity', 'golden.json');

/** Loads the shared rule catalog from the canonical YAML (single source of truth). */
export function loadCanonicalCatalog(): RuleCatalog {
  return RuleCatalog.fromYaml(fs.readFileSync(CANONICAL_RULES, 'utf8'));
}

/** Loads the shared convention catalog from the canonical YAML (single source of truth). */
export function loadCanonicalConventions(): ConventionCatalog {
  return ConventionCatalog.fromYaml(fs.readFileSync(CANONICAL_CONVENTIONS, 'utf8'));
}

/** Reads a corpus file given its golden key, e.g. "fixtures/4.15-db-connection-refused.log". */
export function readCorpusFile(goldenKey: string): string {
  const [label, ...rest] = goldenKey.split('/');
  const fileName = rest.join('/');
  const dir =
    label === 'fixtures'
      ? path.join(REPO_ROOT, 'src', 'main', 'resources', 'fixtures')
      : path.join(REPO_ROOT, 'src', 'test', 'resources', 'real-world-logs');
  return fs.readFileSync(path.join(dir, fileName), 'utf8');
}
