// Port of com.springdebugger.extractor.LogFilePropertyFinder. Discovers each Spring Boot app's
// configured log file by reading logging.file.name (or legacy logging.file) from application*.
// properties / yml across the project, resolving relative paths against the owning module so a
// multi-module build yields one log file per service.
import * as fs from 'fs';
import * as path from 'path';
import * as yaml from 'js-yaml';

/** Reads logging.file.name/logging.file from .properties content, or null. */
export function fromProperties(content: string | null): string | null {
  if (content === null) return null;
  const props = parseProperties(content);
  const v = props.get('logging.file.name') ?? props.get('logging.file');
  return blankToNull(v ?? null);
}

/** Reads logging.file.name/logging.file from YAML content, or null. */
export function fromYaml(content: string | null): string | null {
  if (content === null || content.trim() === '') return null;
  try {
    const root = yaml.load(content);
    if (typeof root !== 'object' || root === null) return null;
    const map = root as Record<string, unknown>;
    let flat = nestedString(map, 'logging.file.name');
    if (flat === null) flat = nestedString(map, 'logging.file');
    return blankToNull(flat);
  } catch {
    return null;
  }
}

/**
 * Finds every application*.properties/yml under the tree that declares a log file and returns the
 * resolved absolute log path for each, de-duplicated preserving discovery order.
 */
export function discoverAll(base: string): string[] {
  const out = new Set<string>();
  if (!isDirectory(base)) return [];
  const configs: string[] = [];
  collectConfigs(base, 0, configs);
  for (const cfg of configs) {
    const content = read(cfg);
    const declared = cfg.endsWith('.properties') ? fromProperties(content) : fromYaml(content);
    if (declared === null) continue;
    out.add(resolveAgainstModule(cfg, declared));
  }
  return Array.from(out);
}

/** Resolves a declared log path against the module owning a config file. */
export function resolveAgainstModule(configFile: string, declaredPath: string): string {
  if (path.isAbsolute(declaredPath)) return declaredPath;
  const normalised = configFile.split(path.sep).join('/');
  const srcIdx = normalised.indexOf('/src/');
  const moduleRoot = srcIdx > 0 ? normalised.substring(0, srcIdx) : path.dirname(configFile);
  return path.join(moduleRoot, declaredPath);
}

function collectConfigs(dir: string, depth: number, out: string[]): void {
  if (depth > 10) return;
  let entries: fs.Dirent[];
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return;
  }
  for (const e of entries) {
    const full = path.join(dir, e.name);
    if (e.isFile()) {
      const n = e.name;
      const isConfig =
        n.startsWith('application') &&
        (n.endsWith('.properties') || n.endsWith('.yml') || n.endsWith('.yaml'));
      if (isConfig) out.push(full);
    } else if (e.isDirectory()) {
      const n = e.name;
      // Config lives under src/main/resources, so do NOT prune src; only skip caches/outputs.
      if (
        n === '.git' ||
        n === '.gradle' ||
        n === '.idea' ||
        n === 'node_modules' ||
        n === 'build' ||
        n === 'target' ||
        n === '.m2'
      ) {
        continue;
      }
      collectConfigs(full, depth + 1, out);
    }
  }
}

function nestedString(map: Record<string, unknown>, dottedKey: string): string | null {
  const direct = map[dottedKey];
  if (direct !== undefined && direct !== null) return String(direct);
  let node: unknown = map;
  for (const part of dottedKey.split('.')) {
    if (typeof node !== 'object' || node === null) return null;
    node = (node as Record<string, unknown>)[part];
    if (node === undefined || node === null) return null;
  }
  return String(node);
}

/** Minimal java.util.Properties-style parser: key=value or key:value, # and ! comments. */
function parseProperties(content: string): Map<string, string> {
  const map = new Map<string, string>();
  for (const raw of content.split(/\r\n|\r|\n/)) {
    const line = raw.trim();
    if (line === '' || line.startsWith('#') || line.startsWith('!')) continue;
    let sep = -1;
    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (ch === '=' || ch === ':') {
        sep = i;
        break;
      }
    }
    if (sep < 0) continue;
    const key = line.substring(0, sep).trim();
    const value = line.substring(sep + 1).trim();
    if (key !== '') map.set(key, value);
  }
  return map;
}

function isDirectory(p: string): boolean {
  try {
    return fs.statSync(p).isDirectory();
  } catch {
    return false;
  }
}

function read(file: string): string | null {
  try {
    return fs.readFileSync(file, 'utf8');
  } catch {
    return null;
  }
}

function blankToNull(s: string | null): string | null {
  return s === null || s.trim() === '' ? null : s.trim();
}
