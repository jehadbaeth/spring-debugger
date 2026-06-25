// Port of com.springdebugger.extractor.LogRunBoundary. A Spring Boot file appender does not truncate
// between runs, so one log accumulates every bootRun. To avoid surfacing stale errors and to let an
// identical error re-surface on a re-run, the tailer diagnoses only the slice of the most recent run.

/** True if the line is a Spring Boot application start line. */
export function isRunStart(line: string | null): boolean {
  if (line === null) return false;
  return line.includes('Starting ') && line.includes('with PID');
}

/** True if any line in the chunk starts a new run. */
export function containsRunStart(text: string | null): boolean {
  if (text === null || text === '') return false;
  for (const line of text.split('\n')) {
    if (isRunStart(line)) return true;
  }
  return false;
}

/**
 * Returns the text from the last run-start line onward (most recent run only). If no run-start line
 * is present, the whole text is returned unchanged.
 */
export function lastRunSlice(text: string | null): string {
  if (text === null || text === '') return text ?? '';
  const lines = text.split('\n');
  let lastStart = -1;
  for (let i = 0; i < lines.length; i++) {
    if (isRunStart(lines[i])) lastStart = i;
  }
  if (lastStart <= 0) return text;
  return lines.slice(lastStart).join('\n');
}
