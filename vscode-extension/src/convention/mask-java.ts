// Blanks out string/char literals and comments in a Java source text while preserving length and
// newlines, so regex-based member scanning never mistakes braces/parens/commas inside a literal or
// a comment for real syntax. A single-pass state machine (not sequential regex replace) so a `//`
// inside a string, or a string marker inside a comment, is handled correctly.
export function maskJava(text: string): string {
  const out: string[] = new Array(text.length);
  let i = 0;
  const n = text.length;
  while (i < n) {
    const c = text[i];
    const next = i + 1 < n ? text[i + 1] : '';

    if (c === '/' && next === '/') {
      while (i < n && text[i] !== '\n') {
        out[i] = text[i] === '\n' ? '\n' : ' ';
        i++;
      }
      continue;
    }
    if (c === '/' && next === '*') {
      out[i] = ' ';
      out[i + 1] = ' ';
      i += 2;
      while (i < n && !(text[i] === '*' && i + 1 < n && text[i + 1] === '/')) {
        out[i] = text[i] === '\n' ? '\n' : ' ';
        i++;
      }
      if (i < n) {
        out[i] = ' ';
        out[i + 1] = ' ';
        i += 2;
      }
      continue;
    }
    if (c === '"') {
      out[i] = ' ';
      i++;
      while (i < n && text[i] !== '"') {
        if (text[i] === '\\' && i + 1 < n) {
          out[i] = ' ';
          out[i + 1] = ' ';
          i += 2;
          continue;
        }
        out[i] = text[i] === '\n' ? '\n' : ' ';
        i++;
      }
      if (i < n) {
        out[i] = ' ';
        i++;
      }
      continue;
    }
    if (c === "'") {
      out[i] = ' ';
      i++;
      while (i < n && text[i] !== "'") {
        if (text[i] === '\\' && i + 1 < n) {
          out[i] = ' ';
          out[i + 1] = ' ';
          i += 2;
          continue;
        }
        out[i] = text[i] === '\n' ? '\n' : ' ';
        i++;
      }
      if (i < n) {
        out[i] = ' ';
        i++;
      }
      continue;
    }
    out[i] = c;
    i++;
  }
  return out.join('');
}
