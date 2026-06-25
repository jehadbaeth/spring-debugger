// Port of com.springdebugger.extractor.StackTraceSegmenter. Splits a noisy buffer into per-error
// blocks. Note Java splits on "\n" only (limit -1), so any \r stays attached to a line; JS
// String.split('\n') keeps trailing empties too, matching the -1 limit exactly.

const TOP_LEVEL = /^([\w.$]+(?:Exception|Error))(?::|\s|$)/;

export function segment(text: string | null | undefined): string[] {
  if (text === null || text === undefined || text.trim() === '') return [];

  const lines = text.split('\n');
  const boundaries: number[] = [];
  for (let i = 0; i < lines.length; i++) {
    if (isBoundary(lines[i])) boundaries.push(i);
  }
  if (boundaries.length <= 1) {
    return [text];
  }

  const regions: string[] = [];
  for (let k = 0; k < boundaries.length; k++) {
    const from = boundaries[k];
    const to = k + 1 < boundaries.length ? boundaries[k + 1] : lines.length;
    regions.push(lines.slice(from, to).join('\n'));
  }
  return regions;
}

function isBoundary(line: string): boolean {
  if (TOP_LEVEL.test(line)) return true;
  if (line.includes('Resolved [') || line.includes('No endpoint ')) return true;
  // Connection-failure WARN spam carries no exception or stack; treat its marker as a boundary so
  // it is diagnosed in its own right (dedup collapses the repeats downstream).
  return line.includes('could not be established');
}
