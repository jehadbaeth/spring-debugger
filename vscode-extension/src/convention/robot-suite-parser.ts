// Port of com.springdebugger.convention.robot.RobotSuiteParser. Pure, line-based parser for a Robot
// Framework suite file. No PSI, no IO: text in, RobotSuite out, with absolute text offsets for
// highlighting. Mirrors the Java implementation line for line so the two engines parse identically.
import { RobotMetadata, RobotSuite, RobotTestCase, TextRange } from './robot-suite';

const HEADER = /^\*+\s*(.+?)\s*\*+\s*$/;
const SEPARATOR = /(?: {2,}|\t+)/;

export function parseRobotSuite(text: string): RobotSuite {
  const metadata: RobotMetadata[] = [];
  const testCases: RobotTestCase[] = [];
  let hasTestCasesSection = false;
  let settingsHeaderRange: TextRange | null = null;

  const lines = text.split('\n');
  let lineStart = 0;
  let section: string | null = null;
  let current: RobotTestCase | null = null;
  let inTagsContinuation = false;

  for (const raw of lines) {
    const line = raw.endsWith('\r') ? raw.substring(0, raw.length - 1) : raw;
    const start = lineStart;
    lineStart += raw.length + 1; // +1 for the split-removed '\n'

    const trimmed = line.trim();
    if (trimmed === '') continue;

    const header = headerName(trimmed);
    if (header !== null) {
      if (header.startsWith('setting')) {
        section = 'SETTINGS';
        settingsHeaderRange = contentRange(line, start);
      } else if (header.startsWith('test case') || header.startsWith('task')) {
        section = 'TEST_CASES';
        hasTestCasesSection = true;
      } else if (header.startsWith('variable')) {
        section = 'VARIABLES';
      } else if (header.startsWith('keyword')) {
        section = 'KEYWORDS';
      } else {
        section = 'OTHER';
      }
      current = null;
      inTagsContinuation = false;
      continue;
    }

    if (trimmed.startsWith('#')) continue;

    if (section === 'SETTINGS') {
      const tokens = splitCells(trimmed);
      if (tokens.length >= 2 && tokens[0].toLowerCase() === 'metadata') {
        const name = tokens[1];
        const value = tokens.length >= 3 ? tokens.slice(2).join(' ') : '';
        metadata.push({ name, value, lineRange: contentRange(line, start) });
      }
    } else if (section === 'TEST_CASES') {
      const indented = line.length > 0 && (line[0] === ' ' || line[0] === '\t');
      if (!indented) {
        current = { name: trimmed, nameRange: contentRange(line, start), hasDocumentation: false, hasTags: false, tags: [] };
        testCases.push(current);
        inTagsContinuation = false;
      } else if (current !== null) {
        const tokens = splitCells(trimmed);
        const first = tokens.length === 0 ? '' : tokens[0];
        if (first.toLowerCase() === '[documentation]') {
          current.hasDocumentation = true;
          inTagsContinuation = false;
        } else if (first.toLowerCase() === '[tags]') {
          current.hasTags = true;
          addTags(current, tokens, 1);
          inTagsContinuation = true;
        } else if (first === '...' && inTagsContinuation) {
          addTags(current, tokens, 1);
        } else {
          inTagsContinuation = false;
        }
      }
    }
  }
  return { settingsHeaderRange, metadata, testCases, hasTestCasesSection };
}

function headerName(trimmed: string): string | null {
  const m = HEADER.exec(trimmed);
  return m ? m[1].toLowerCase() : null;
}

function splitCells(trimmed: string): string[] {
  return trimmed.split(SEPARATOR).filter((t) => t !== '');
}

function addTags(tc: RobotTestCase, tokens: string[], from: number): void {
  for (let i = from; i < tokens.length; i++) tc.tags.push(tokens[i]);
}

/** Absolute range covering the trimmed content of a line (first to last non-whitespace char). */
function contentRange(line: string, lineStart: number): TextRange {
  let begin = 0;
  while (begin < line.length && /\s/.test(line[begin])) begin++;
  let end = line.length;
  while (end > begin && /\s/.test(line[end - 1])) end--;
  if (begin >= end) return { start: lineStart, end: lineStart + Math.max(0, line.length) };
  return { start: lineStart + begin, end: lineStart + end };
}
