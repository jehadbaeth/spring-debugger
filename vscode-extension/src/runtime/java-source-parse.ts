// Pure, unit-tested parsing of a Java source file into the facts the PsiEnricher needs. This is the
// VS Code substitute for IntelliJ PSI: it reads annotations, package, and interface/class from text.
// It is intentionally lightweight (no full Java grammar); the symbol provider locates the file and
// type, and this turns that one file into ClassFacts. Library types are never parsed here (they are
// not in workspace source), so the "third-party @Bean" branch of the enricher does not fire from VS
// Code resolution. That limitation is documented and acceptable.
import { ClassFacts } from '../engine';

const MODIFIERS = new Set([
  'public',
  'private',
  'protected',
  'abstract',
  'final',
  'sealed',
  'non-sealed',
  'strictfp',
  'static',
]);

/** The package declared in the file, or null. */
export function packageOf(text: string): string | null {
  const m = /^\s*package\s+([\w.]+)\s*;/m.exec(text);
  return m ? m[1] : null;
}

/**
 * Builds ClassFacts for the named type if it is declared in this source text, else null. Annotations
 * are the simple names on the type declaration (e.g. "Service", "Mapper"). inProjectSource is true
 * because the text comes from a workspace file.
 */
export function buildClassFacts(text: string, simpleName: string, fileName: string): ClassFacts | null {
  const decl = findTypeDecl(text, simpleName);
  if (decl === null) return null;
  const pkg = packageOf(text);
  const fqn = pkg ? pkg + '.' + simpleName : simpleName;
  const annotations = gatherAnnotations(text.substring(0, decl.index));
  return new ClassFacts(fqn, pkg ?? '', decl.keyword === 'interface', annotations, true, true, fileName);
}

interface TypeDecl {
  index: number;
  keyword: string;
}

function findTypeDecl(text: string, simpleName: string): TypeDecl | null {
  const re = new RegExp(`\\b(class|interface|enum|record)\\s+${escapeRegExp(simpleName)}\\b`);
  const m = re.exec(text);
  return m ? { index: m.index, keyword: m[1] } : null;
}

/** Collects @Annotation simple names attached to the type declaration, walking back over its lines. */
function gatherAnnotations(textBeforeDecl: string): Set<string> {
  const out = new Set<string>();
  const lines = textBeforeDecl.split('\n');
  for (let i = lines.length - 1; i >= 0; i--) {
    const line = lines[i].trim();
    if (line === '' || line.startsWith('//') || line.startsWith('*') || line.startsWith('/*')) continue;
    if (line.includes('@')) {
      for (const name of annotationNames(line)) out.add(name);
      // If this line also closes a previous member/import, stop after collecting it.
      if (line.includes(';') || line.includes('}') || line.includes('{')) break;
      continue;
    }
    if (isOnlyModifiers(line)) continue;
    break;
  }
  return out;
}

function annotationNames(line: string): string[] {
  const names: string[] = [];
  const re = /@([\w.]+)/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(line)) !== null) {
    const full = m[1];
    const dot = full.lastIndexOf('.');
    names.push(dot >= 0 ? full.substring(dot + 1) : full);
  }
  return names;
}

function isOnlyModifiers(line: string): boolean {
  const tokens = line.split(/\s+/).filter((t) => t !== '');
  return tokens.length > 0 && tokens.every((t) => MODIFIERS.has(t));
}

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
