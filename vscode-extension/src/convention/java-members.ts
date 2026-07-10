// The member-level "PSI substitute" for the VS Code convention checks: fields, methods, parameters,
// and their annotations. Extends the class-only facts in runtime/java-source-parse.ts. Hand-rolled
// regex over a comment/string-masked copy of the source (see mask-java.ts), matching the same
// "no full grammar" tradeoff already accepted on the IntelliJ side's simple-name annotation
// matching. Known limitation: unusual formatting (deeply nested generics, multi-line signatures
// split mid-token, member declarations for multiple names on one line) can be missed. This is a
// deliberate, documented tradeoff, not an oversight.
import { TextRange } from './robot-suite';

export interface JavaAnnotationRef {
  name: string;
  /** Raw text between the annotation's parentheses, or null if the annotation has no parens. */
  args: string | null;
  /** Span of the full annotation (from `@` through the closing paren, if any). */
  range: TextRange;
}

export interface JavaParameter {
  name: string;
  type: string;
  annotations: JavaAnnotationRef[];
  nameRange: TextRange;
}

export interface JavaMethod {
  name: string;
  returnType: string;
  modifiers: string[];
  annotations: JavaAnnotationRef[];
  parameters: JavaParameter[];
  hasBody: boolean;
  hasJavadoc: boolean;
  nameRange: TextRange;
  bodyRange: TextRange | null;
  enclosingClassIndex: number;
}

export interface JavaField {
  name: string;
  type: string;
  annotations: JavaAnnotationRef[];
  nameRange: TextRange;
  enclosingClassIndex: number;
}

export interface JavaClassInfo {
  name: string;
  keyword: string;
  annotations: JavaAnnotationRef[];
  nameRange: TextRange;
  bodyRange: TextRange | null;
}

export interface JavaFileFacts {
  classes: JavaClassInfo[];
  fields: JavaField[];
  methods: JavaMethod[];
}

const STATEMENT_KEYWORDS = new Set([
  'if', 'for', 'while', 'switch', 'catch', 'else', 'do', 'try', 'return', 'throw', 'new', 'assert',
  'yield', 'case', 'synchronized', 'instanceof', 'this', 'super', 'finally', 'package', 'import',
]);

const METHOD_MODIFIERS = new Set([
  'public', 'private', 'protected', 'static', 'final', 'abstract', 'synchronized', 'native',
  'default', 'strictfp',
]);

const FIELD_MODIFIERS = new Set(['public', 'private', 'protected', 'static', 'final', 'transient', 'volatile']);

const CLASS_RE = /\b(class|interface|enum|record)\s+(\w+)/g;
const METHOD_RE =
  /\b((?:(?:public|private|protected|static|final|abstract|synchronized|native|default|strictfp)\s+)*)(?:<[^<>]*>\s+)?([\w$]+(?:\.[\w$]+)*(?:<[^<>]*>)?(?:\[\])*)\s+(\w+)\s*\(([^()]*)\)\s*(?:throws\s+[\w.$,\s]+)?\s*([{;])/g;
const FIELD_RE =
  /\b((?:(?:public|private|protected|static|final|transient|volatile)\s+)*)([\w$]+(?:\.[\w$]+)*(?:<[^<>]*>)?(?:\[\])*)\s+(\w+)\s*(=[^;]*)?;/g;

/**
 * Declarations can be packed on one line (single-line test fixtures, dense formatting), so
 * METHOD_RE/FIELD_RE no longer anchor on line start. To keep them from matching mid-expression
 * text (e.g. a method call embedded in a statement), every match is required to sit right after a
 * statement boundary (`;`, `{`, `}`, or start of file) once any preceding annotation is skipped over.
 * Annotations aren't part of METHOD_RE/FIELD_RE's own grammar (they're recovered separately by
 * parseAnnotationsBefore), so they're blanked out here to make the boundary check trivial.
 */
function maskAnnotationsForScan(masked: string): string {
  const chars = masked.split('');
  const re = /@[\w.]+(\([^)]*\))?/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(masked)) !== null) {
    for (let i = m.index; i < m.index + m[0].length; i++) {
      if (chars[i] !== '\n') chars[i] = ' ';
    }
  }
  return chars.join('');
}

function isAfterStatementBoundary(scanMasked: string, index: number): boolean {
  let i = index;
  while (i > 0 && /\s/.test(scanMasked[i - 1])) i--;
  if (i === 0) return true;
  return scanMasked[i - 1] === ';' || scanMasked[i - 1] === '{' || scanMasked[i - 1] === '}';
}

export function parseJavaFile(text: string, masked: string): JavaFileFacts {
  const classes = parseClasses(text, masked);
  const methods = parseMethods(text, masked);
  const bodyRanges = methods.filter((m) => m.bodyRange !== null).map((m) => m.bodyRange as TextRange);
  const fields = parseFields(text, masked, bodyRanges);

  assignEnclosingClass(classes, methods, (m) => m.nameRange.start);
  assignEnclosingClass(classes, fields, (f) => f.nameRange.start);

  return { classes, fields, methods };
}

function assignEnclosingClass<T extends { enclosingClassIndex: number }>(
  classes: JavaClassInfo[],
  members: T[],
  posOf: (m: T) => number,
): void {
  for (const member of members) {
    const pos = posOf(member);
    let best = -1;
    let bestSize = Infinity;
    for (let i = 0; i < classes.length; i++) {
      const range = classes[i].bodyRange;
      if (!range) continue;
      if (pos >= range.start && pos < range.end) {
        const size = range.end - range.start;
        if (size < bestSize) {
          bestSize = size;
          best = i;
        }
      }
    }
    member.enclosingClassIndex = best;
  }
}

function parseClasses(text: string, masked: string): JavaClassInfo[] {
  const out: JavaClassInfo[] = [];
  CLASS_RE.lastIndex = 0;
  let m: RegExpExecArray | null;
  while ((m = CLASS_RE.exec(masked)) !== null) {
    const keyword = m[1];
    const name = m[2];
    const nameStart = m.index + m[0].length - name.length;
    const nameRange: TextRange = { start: nameStart, end: nameStart + name.length };
    const annotations = parseAnnotationsBefore(text, masked, m.index);
    const braceStart = masked.indexOf('{', m.index + m[0].length);
    const bodyRange = braceStart >= 0 ? matchBraces(masked, braceStart) : null;
    out.push({ name, keyword, annotations, nameRange, bodyRange });
  }
  return out;
}

function parseMethods(text: string, masked: string): JavaMethod[] {
  const out: JavaMethod[] = [];
  const scanMasked = maskAnnotationsForScan(masked);
  METHOD_RE.lastIndex = 0;
  let m: RegExpExecArray | null;
  while ((m = METHOD_RE.exec(scanMasked)) !== null) {
    const modifiersText = m[1] ?? '';
    const returnType = m[2];
    const name = m[3];
    const paramsText = m[4];
    const terminator = m[5];

    if (!isAfterStatementBoundary(scanMasked, m.index)) continue;
    if (STATEMENT_KEYWORDS.has(returnType) || STATEMENT_KEYWORDS.has(name)) continue;
    if (METHOD_MODIFIERS.has(returnType) || FIELD_MODIFIERS.has(returnType)) continue;

    const nameIndexInMatch = findNameIndex(m[0], name, paramsText);
    const absoluteNameStart = m.index + nameIndexInMatch;
    const nameRange: TextRange = { start: absoluteNameStart, end: absoluteNameStart + name.length };

    const modifiers = modifiersText.split(/\s+/).filter((t) => METHOD_MODIFIERS.has(t));
    const annotations = parseAnnotationsBefore(text, masked, m.index);
    const parenStart = masked.indexOf('(', absoluteNameStart);
    const parameters = parseParameters(text, masked, parenStart + 1, parenStart + 1 + paramsText.length);
    const hasBody = terminator === '{';
    const bodyStartAbs = m.index + m[0].length - 1;
    const bodyRange = hasBody ? matchBraces(masked, bodyStartAbs) : null;
    const hasJavadoc = hasJavadocBefore(text, m.index);

    out.push({
      name,
      returnType,
      modifiers,
      annotations,
      parameters,
      hasBody,
      hasJavadoc,
      nameRange,
      bodyRange,
      enclosingClassIndex: -1,
    });
  }
  return out;
}

function findNameIndex(matchText: string, name: string, paramsText: string): number {
  const parenIdx = matchText.indexOf('(' + paramsText);
  if (parenIdx < 0) {
    const idx = matchText.lastIndexOf(name);
    return idx >= 0 ? idx : 0;
  }
  const before = matchText.substring(0, parenIdx);
  const idx = before.lastIndexOf(name);
  return idx >= 0 ? idx : 0;
}

function parseFields(text: string, masked: string, methodBodyRanges: TextRange[]): JavaField[] {
  const out: JavaField[] = [];
  const scanMasked = maskAnnotationsForScan(masked);
  FIELD_RE.lastIndex = 0;
  let m: RegExpExecArray | null;
  while ((m = FIELD_RE.exec(scanMasked)) !== null) {
    const type = m[2];
    const name = m[3];
    if (!isAfterStatementBoundary(scanMasked, m.index)) continue;
    if (STATEMENT_KEYWORDS.has(type) || STATEMENT_KEYWORDS.has(name)) continue;
    if (METHOD_MODIFIERS.has(type) || FIELD_MODIFIERS.has(type)) continue;

    const absoluteNameStart = findFieldNameStart(m[0], m.index, type, name);
    if (insideAny(absoluteNameStart, methodBodyRanges)) continue;

    const nameRange: TextRange = { start: absoluteNameStart, end: absoluteNameStart + name.length };
    const annotations = parseAnnotationsBefore(text, masked, m.index);
    out.push({ name, type, annotations, nameRange, enclosingClassIndex: -1 });
  }
  return out;
}

function findFieldNameStart(matchText: string, matchIndex: number, type: string, name: string): number {
  const typeIdx = matchText.indexOf(type);
  const searchFrom = typeIdx >= 0 ? typeIdx + type.length : 0;
  const idx = matchText.indexOf(name, searchFrom);
  return matchIndex + (idx >= 0 ? idx : 0);
}

function insideAny(pos: number, ranges: TextRange[]): boolean {
  return ranges.some((r) => pos >= r.start && pos < r.end);
}

/** Finds the matching closing brace for the '{' at `openIndex`, scanning masked text (no strings/comments). */
function matchBraces(masked: string, openIndex: number): TextRange {
  let depth = 0;
  for (let i = openIndex; i < masked.length; i++) {
    if (masked[i] === '{') depth++;
    else if (masked[i] === '}') {
      depth--;
      if (depth === 0) return { start: openIndex, end: i + 1 };
    }
  }
  return { start: openIndex, end: masked.length };
}

/** Parses a top-level-comma-separated parameter list, respecting <>, (), [] nesting via masked text. */
function parseParameters(text: string, masked: string, start: number, end: number): JavaParameter[] {
  const chunk = masked.substring(start, end);
  const rawChunk = text.substring(start, end);
  const parts: { text: string; start: number }[] = [];
  let depth = 0;
  let partStart = 0;
  for (let i = 0; i <= chunk.length; i++) {
    const c = i < chunk.length ? chunk[i] : ',';
    if (c === '<' || c === '(' || c === '[') depth++;
    else if (c === '>' || c === ')' || c === ']') depth--;
    else if (c === ',' && depth === 0) {
      parts.push({ text: chunk.substring(partStart, i), start: partStart });
      partStart = i + 1;
    }
  }
  const params: JavaParameter[] = [];
  for (const part of parts) {
    if (part.text.trim() === '') continue;
    const parsed = parseOneParameter(rawChunk, part.text, part.start, start);
    if (parsed) params.push(parsed);
  }
  return params;
}

function parseOneParameter(
  rawChunk: string,
  maskedPart: string,
  partStartInChunk: number,
  chunkAbsoluteStart: number,
): JavaParameter | null {
  const rawPart = rawChunk.substring(partStartInChunk, partStartInChunk + maskedPart.length);
  const annotations = parseAnnotationsInline(rawPart, maskedPart, chunkAbsoluteStart + partStartInChunk);
  const withoutAnnotations = stripLeadingAnnotations(maskedPart);
  const trimmedOffset = maskedPart.length - withoutAnnotations.length;
  const m = /(?:final\s+)?([\w$]+(?:\.[\w$]+)*(?:<[^<>]*>)?(?:\[\])*(?:\.\.\.)?)\s+(\w+)\s*$/.exec(
    withoutAnnotations.trimEnd(),
  );
  if (!m) return null;
  const type = m[1];
  const name = m[2];
  const nameIdxInWithout = withoutAnnotations.lastIndexOf(name);
  const nameStart = chunkAbsoluteStart + partStartInChunk + trimmedOffset + nameIdxInWithout;
  return {
    name,
    type,
    annotations,
    nameRange: { start: nameStart, end: nameStart + name.length },
  };
}

function stripLeadingAnnotations(maskedPart: string): string {
  let s = maskedPart;
  const re = /^\s*@[\w.]+(\([^)]*\))?/;
  let m: RegExpExecArray | null;
  while ((m = re.exec(s)) !== null) {
    s = s.substring(m[0].length);
  }
  return s;
}

function parseAnnotationsInline(rawPart: string, maskedPart: string, baseOffset: number): JavaAnnotationRef[] {
  const out: JavaAnnotationRef[] = [];
  const re = /@([\w.]+)(\(([^)]*)\))?/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(maskedPart)) !== null) {
    const dotted = m[1];
    const simple = dotted.includes('.') ? dotted.substring(dotted.lastIndexOf('.') + 1) : dotted;
    const args = m[3] !== undefined ? rawPart.substring(m.index + m[0].indexOf('(') + 1, m.index + m[0].length - 1) : null;
    const range: TextRange = { start: baseOffset + m.index, end: baseOffset + m.index + m[0].length };
    out.push({ name: simple, args, range });
  }
  return out;
}

/** Walks backward from `declStart` over annotation and modifier-only lines, collecting annotations. */
function parseAnnotationsBefore(text: string, masked: string, declStart: number): JavaAnnotationRef[] {
  const out: JavaAnnotationRef[] = [];
  const before = masked.substring(0, declStart);
  const rawBefore = text.substring(0, declStart);
  const lines = before.split('\n');
  const rawLines = rawBefore.split('\n');
  let offset = before.length;
  for (let i = lines.length - 1; i >= 0; i--) {
    const line = lines[i];
    const trimmed = line.trim();
    const lineStart = offset - line.length - (i < lines.length - 1 ? 1 : 0);
    offset = lineStart - 1;
    if (trimmed === '') continue;
    if (trimmed.includes('@')) {
      out.unshift(...parseAnnotationsInline(rawLines[i], line, lineStart));
      if (trimmed.includes(';') || trimmed.includes('}') || trimmed.includes('{')) break;
      continue;
    }
    if (isOnlyModifiers(trimmed)) continue;
    break;
  }
  return out;
}

function isOnlyModifiers(trimmed: string): boolean {
  const tokens = trimmed.split(/\s+/).filter((t) => t !== '');
  return (
    tokens.length > 0 &&
    tokens.every((t) => METHOD_MODIFIERS.has(t) || FIELD_MODIFIERS.has(t) || /^<.*>$/.test(t))
  );
}

/** True if a `/** ... *&#47;` block comment sits immediately before the annotation/modifier run preceding `declStart`. */
function hasJavadocBefore(text: string, declStart: number): boolean {
  const before = text.substring(0, declStart);
  const lines = before.split('\n');
  let offset = before.length;
  for (let i = lines.length - 1; i >= 0; i--) {
    const line = lines[i];
    const trimmed = line.trim();
    offset -= line.length + (i < lines.length - 1 ? 1 : 0);
    if (trimmed === '') continue;
    if (trimmed.includes('@') || isOnlyModifiers(trimmed) || /^<.*>$/.test(trimmed)) continue;
    if (trimmed.endsWith('*/')) {
      const lineEnd = offset + line.length;
      const commentStart = text.lastIndexOf('/**', lineEnd);
      const commentEnd = commentStart >= 0 ? text.indexOf('*/', commentStart) : -1;
      return commentStart >= 0 && commentEnd >= 0 && commentEnd <= lineEnd;
    }
    return false;
  }
  return false;
}
