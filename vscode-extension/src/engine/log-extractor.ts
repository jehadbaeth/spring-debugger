// Port of com.springdebugger.extractor.LogExtractor. Parses a raw block of Spring Boot output into
// a RawSignal. Java's \R (any line break) becomes (?:\r\n|\r|\n); (?m) becomes the m flag. The
// banner is read with indexOf logic, exactly like the Java extractBannerSection (the BANNER_*
// regexes in the Java file are dead code and are intentionally not ported).
import { Phase, RawSignal } from './models';

const CAUSED_BY = /^\s*Caused by:\s*([\w.$]+):\s*(.*)$/gm;
const NESTED_EXCEPTION =
  /nested exception is\s+([\w.$]+):\s*(.*?)(?=;\s*nested exception is|\r\n|\r|\n|$)/g;
const TOP_LEVEL_EXCEPTION = /^([\w.$]+(?:Exception|Error)):\s+(.+)$/m;
const BEAN_CREATION_ERROR = /Error creating bean with name '([^']+)'/;
const HTTP_STATUS = /(?:ResponseStatus|status=|HTTP\/)\s*(\d{3})/;
const BUILD_ERROR_LINE = /(?:error:|cannot find symbol|compilation failed|build failed)(.*)/i;

const EXCERPT_WINDOW = 8000;

export function extract(rawText: string, phase: Phase): RawSignal {
  const lines = rawText.split(/\r\n|\r|\n/);

  let deepestCausedByClass: string | null = null;
  let deepestCausedByMessage: string | null = null;
  const bannerDescription = extractBannerSection(rawText, 'Description:');
  const bannerAction = extractBannerSection(rawText, 'Action:');
  let failingBeanName: string | null = null;
  let httpStatus = -1;
  const relevantLines: string[] = [];

  // The deepest "Caused by:" wins: iterate all, keep the last.
  const caused = lastMatch(CAUSED_BY, rawText);
  if (caused) {
    deepestCausedByClass = caused[1].trim();
    deepestCausedByMessage = caused[2].trim();
  }

  // Fallback: inline "nested exception is" style with no "Caused by:" lines.
  if (deepestCausedByClass === null) {
    const nested = lastMatch(NESTED_EXCEPTION, rawText);
    if (nested) {
      deepestCausedByClass = nested[1].trim();
      deepestCausedByMessage = nested[2].trim();
    }
  }

  // Last resort: a bare top-level exception line with no cause chain at all (first match).
  if (deepestCausedByClass === null) {
    const top = TOP_LEVEL_EXCEPTION.exec(rawText);
    if (top) {
      deepestCausedByClass = top[1].trim();
      deepestCausedByMessage = top[2].trim();
    }
  }

  const bean = BEAN_CREATION_ERROR.exec(rawText);
  if (bean) failingBeanName = bean[1];

  const http = HTTP_STATUS.exec(rawText);
  if (http) {
    const parsed = parseInt(http[1], 10);
    if (!Number.isNaN(parsed)) httpStatus = parsed;
  }

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed.length === 0) continue;
    if (isRelevantLine(trimmed)) relevantLines.push(trimmed);
  }

  const excerptStart = Math.max(0, rawText.length - EXCERPT_WINDOW);
  const excerpt = rawText.substring(excerptStart);

  return new RawSignal(
    phase,
    deepestCausedByClass,
    deepestCausedByMessage,
    bannerDescription,
    bannerAction,
    failingBeanName,
    httpStatus,
    relevantLines,
    excerpt,
  );
}

function extractBannerSection(text: string, sectionHeader: string): string | null {
  const idx = text.indexOf(sectionHeader);
  if (idx < 0) return null;
  let contentStart = idx + sectionHeader.length;
  while (
    contentStart < text.length &&
    (text.charAt(contentStart) === '\n' ||
      text.charAt(contentStart) === '\r' ||
      text.charAt(contentStart) === ' ')
  ) {
    contentStart++;
  }
  let end = text.indexOf('\n\n', contentStart);
  if (end < 0) end = Math.min(contentStart + 500, text.length);
  return text.substring(contentStart, end).trim();
}

function isRelevantLine(line: string): boolean {
  return (
    line.includes('Exception') ||
    line.includes('Error') ||
    line.includes('Caused by') ||
    line.includes('Description:') ||
    line.includes('Action:') ||
    line.includes('Failed') ||
    line.includes('Cannot') ||
    line.includes('Unable') ||
    line.includes('No qualifying bean') ||
    line.includes('required a bean') ||
    line.includes('required a single bean') ||
    line.includes('Port') ||
    line.includes('could not') ||
    line.includes('No handler') ||
    line.includes('Ambiguous') ||
    line.includes('Unmapped') ||
    line.includes('Cannot determine') ||
    line.includes('MapperImpl') ||
    line.includes('cycle') ||
    line.includes('circular') ||
    BUILD_ERROR_LINE.test(line)
  );
}

/** Runs a global regex over text and returns the last match (mirrors Java while(find()) keeping last). */
function lastMatch(re: RegExp, text: string): RegExpExecArray | null {
  re.lastIndex = 0;
  let last: RegExpExecArray | null = null;
  let m: RegExpExecArray | null;
  while ((m = re.exec(text)) !== null) {
    last = m;
    if (m.index === re.lastIndex) re.lastIndex++; // guard against zero-width matches
  }
  return last;
}
