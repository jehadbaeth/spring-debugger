// Port of com.springdebugger.extractor.TestResultsParser. Extracts failure/error text from a
// JUnit-style results XML (Gradle build/test-results, Maven surefire-reports). The Java version
// uses a DOM parser; here a focused scan over <failure>/<error> elements is enough for this
// well-defined format and avoids pulling in an XML dependency. Failures are emitted before errors,
// matching Java's getElementsByTagName order.

/** One text block per failed/errored test case: its message followed by its stack body. */
export function failureTexts(xml: string | null): string[] {
  const out: string[] = [];
  if (xml === null || xml.trim() === '') return out;
  try {
    for (const tag of ['failure', 'error']) {
      const re = new RegExp(`<${tag}\\b([^>]*?)(?:/>|>([\\s\\S]*?)</${tag}>)`, 'g');
      let m: RegExpExecArray | null;
      while ((m = re.exec(xml)) !== null) {
        const message = attr(m[1], 'message');
        const body = m[2] !== undefined ? decodeXmlContent(m[2]) : '';
        let block = '';
        if (message && message.trim() !== '') block += message + '\n';
        if (body && body.trim() !== '') block += body;
        const text = block.trim();
        if (text !== '') out.push(text);
      }
    }
  } catch {
    return out;
  }
  return out;
}

/** True if the suite XML reports at least one failure or error, cheaply. */
export function hasFailures(xml: string | null): boolean {
  if (xml === null) return false;
  return xml.includes('<failure') || xml.includes('<error');
}

function attr(attrs: string, name: string): string | null {
  const m = new RegExp(`\\b${name}\\s*=\\s*"([^"]*)"`).exec(attrs);
  return m ? decodeEntities(m[1]) : null;
}

function decodeXmlContent(s: string): string {
  // CDATA content is literal; only entity-decode the surrounding text.
  let out = '';
  const re = /<!\[CDATA\[([\s\S]*?)\]\]>/g;
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(s)) !== null) {
    out += decodeEntities(s.slice(last, m.index));
    out += m[1];
    last = re.lastIndex;
  }
  out += decodeEntities(s.slice(last));
  return out;
}

function decodeEntities(s: string): string {
  return s
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
    .replace(/&amp;/g, '&');
}
