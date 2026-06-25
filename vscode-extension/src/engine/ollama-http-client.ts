// Port of com.springdebugger.llm.OllamaClient + OllamaHttpClient. Talks to a local Ollama instance
// (POST /api/generate). Cloud providers are deliberately not implemented: sending error-log text off
// the machine is an exfiltration path, so only local Ollama is acceptable. The call is hard-bounded
// by a timeout so a cold model load cannot stall a poll. The pure protocol helpers are unit tested.

export interface OllamaClient {
  /** Sends a prompt and returns the model's text response, or null on any failure. */
  generate(prompt: string): Promise<string | null>;
}

const DEFAULT_TIMEOUT_MS = 30_000;

export class OllamaHttpClient implements OllamaClient {
  private readonly baseUrl: string;

  constructor(
    baseUrl: string,
    private readonly model: string,
    private readonly timeoutMs: number = DEFAULT_TIMEOUT_MS,
  ) {
    this.baseUrl = baseUrl ? baseUrl.replace(/\/+$/, '') : 'http://localhost:11434';
  }

  async generate(prompt: string): Promise<string | null> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      const response = await fetch(this.baseUrl + '/api/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: buildRequestBody(this.model, prompt),
        signal: controller.signal,
      });
      if (Math.floor(response.status / 100) !== 2) return null;
      return extractResponseField(await response.text());
    } catch {
      return null;
    } finally {
      clearTimeout(timer);
    }
  }
}

/** Builds the Ollama request envelope. format=json asks the model to emit valid JSON. */
export function buildRequestBody(model: string, prompt: string): string {
  return (
    '{"model":' +
    jsonString(model) +
    ',"prompt":' +
    jsonString(prompt) +
    ',"stream":false,"format":"json"}'
  );
}

/** Pulls the "response" field out of the Ollama envelope. Null when absent or unparseable. */
export function extractResponseField(body: string | null): string | null {
  if (body === null || body.trim() === '') return null;
  try {
    const parsed = JSON.parse(body);
    if (parsed && typeof parsed === 'object') {
      const response = (parsed as Record<string, unknown>).response;
      if (typeof response === 'string' && response.trim() !== '') return response;
    }
    return null;
  } catch {
    return null;
  }
}

/** Minimal JSON string escaping for the two values we send. */
export function jsonString(value: string | null): string {
  if (value === null) return '""';
  let sb = '"';
  for (let i = 0; i < value.length; i++) {
    const c = value.charAt(i);
    switch (c) {
      case '"':
        sb += '\\"';
        break;
      case '\\':
        sb += '\\\\';
        break;
      case '\n':
        sb += '\\n';
        break;
      case '\r':
        sb += '\\r';
        break;
      case '\t':
        sb += '\\t';
        break;
      default:
        if (c.charCodeAt(0) < 0x20) {
          sb += '\\u' + c.charCodeAt(0).toString(16).padStart(4, '0');
        } else {
          sb += c;
        }
    }
  }
  return sb + '"';
}
