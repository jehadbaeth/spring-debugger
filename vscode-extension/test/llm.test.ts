import { describe, it, expect } from 'vitest';
import { LlmDiagnosisEngine, RawSignal } from '../src/engine';
import { buildPrompt, parseCard } from '../src/engine/llm-diagnosis-engine';
import { buildRequestBody, extractResponseField, jsonString, OllamaClient } from '../src/engine/ollama-http-client';

// Mirrors Java LlmDiagnosisEngineTest and OllamaHttpClientTest (the pure protocol + safety contract).

function signal(): RawSignal {
  return new RawSignal('STARTUP', 'com.example.WeirdException', 'something odd', null, null, null, -1, ['something odd'], 'stack trace excerpt');
}

function engine(reply: string | null): LlmDiagnosisEngine {
  const client: OllamaClient = { generate: async () => reply };
  return new LlmDiagnosisEngine(client);
}

describe('LlmDiagnosisEngine', () => {
  it('valid JSON reply becomes a MEDIUM llm card', async () => {
    const out = await engine('{"diagnosis":"The thing broke.","fix":"Unbreak it."}').diagnose(signal());
    expect(out).not.toBeNull();
    expect(out!.ruleId).toBe('llm');
    expect(out!.confidence).toBe('MEDIUM');
    expect(out!.diagnosisSentence).toBe('The thing broke.');
    expect(out!.fixSentence).toBe('Unbreak it.');
  });
  it('garbage reply shows nothing', async () => {
    expect(await engine('this is not json at all').diagnose(signal())).toBeNull();
  });
  it('truncated JSON shows nothing', async () => {
    expect(await engine('{"diagnosis":"half a sen').diagnose(signal())).toBeNull();
  });
  it('missing fix field shows nothing', async () => {
    expect(await engine('{"diagnosis":"only diagnosis"}').diagnose(signal())).toBeNull();
  });
  it('blank fields show nothing', async () => {
    expect(await engine('{"diagnosis":"  ","fix":"x"}').diagnose(signal())).toBeNull();
  });
  it('empty reply shows nothing', async () => {
    expect(await engine(null).diagnose(signal())).toBeNull();
  });
  it('prompt includes key signal fields', () => {
    const prompt = buildPrompt(signal());
    expect(prompt).toContain('com.example.WeirdException');
    expect(prompt).toContain('something odd');
    expect(prompt).toContain('STARTUP');
    expect(prompt).toContain('"diagnosis"');
    expect(prompt).toContain('"fix"');
  });
  it('parseCard is a direct safety gate', () => {
    expect(parseCard('not json', signal())).toBeNull();
  });
});

describe('OllamaHttpClient protocol helpers', () => {
  it('request body is valid JSON with escaping', () => {
    const body = buildRequestBody('llama3.2', 'line one\n"quoted"');
    expect(body).toContain('"model":"llama3.2"');
    expect(body).toContain('"stream":false');
    expect(body).toContain('"format":"json"');
    expect(body).toContain('\\n');
    expect(body).toContain('\\"quoted\\"');
  });
  it('extracts the response field', () => {
    const envelope = '{"model":"llama3.2","response":"{\\"diagnosis\\":\\"x\\"}","done":true}';
    expect(extractResponseField(envelope)).toBe('{"diagnosis":"x"}');
  });
  it('missing response field is empty', () => {
    expect(extractResponseField('{"done":true}')).toBeNull();
  });
  it('garbage envelope is empty', () => {
    expect(extractResponseField('not json')).toBeNull();
    expect(extractResponseField('')).toBeNull();
    expect(extractResponseField(null)).toBeNull();
  });
  it('jsonString escapes control characters', () => {
    expect(jsonString('a\tb')).toBe('"a\\tb"');
    expect(jsonString(null)).toBe('""');
  });
});
