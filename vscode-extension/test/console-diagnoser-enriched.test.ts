import { describe, it, expect } from 'vitest';
import { ClassFacts, ConsoleDiagnoser, DiagnosisEngine, EnrichmentContext, PsiEnricher } from '../src/engine';
import { loadCanonicalCatalog } from './helpers';

// Covers the enriched pipeline: parity with the sync path when no layers are given, enrichment only
// with a context, and the once-per-call LLM fallback.

const NO_BEAN_LOG =
  "org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.OrderService' available\n" +
  '\tat org.springframework.beans.factory.support.DefaultListableBeanFactory.x(DefaultListableBeanFactory.java:1)\n';

function diagnoser(): ConsoleDiagnoser {
  return new ConsoleDiagnoser(loadCanonicalCatalog());
}

describe('ConsoleDiagnoser.diagnoseAllEnriched', () => {
  it('with no layers, equals the sync rule-only path', async () => {
    const d = diagnoser();
    const sync = d.diagnoseAll(NO_BEAN_LOG);
    const enriched = await d.diagnoseAllEnriched(NO_BEAN_LOG);
    expect(enriched).toEqual(sync);
  });

  it('does not enrich when no context is supplied even if enrichers are passed', async () => {
    const d = diagnoser();
    const enriched = await d.diagnoseAllEnriched(NO_BEAN_LOG, { enrichers: [new PsiEnricher()] });
    expect(enriched).toEqual(d.diagnoseAll(NO_BEAN_LOG));
  });

  it('enriches a missing-bean card when a context resolves the class', async () => {
    const ctx: EnrichmentContext = {
      findClass: async () => new ClassFacts('com.example.OrderService', 'com.example', false, new Set(), true, true, 'OrderService.java'),
      springBootApplicationPackages: async () => ['com.example'],
      httpGet: async () => null,
    };
    const cards = await diagnoser().diagnoseAllEnriched(NO_BEAN_LOG, { context: ctx, enrichers: [new PsiEnricher()] });
    const bean = cards.find((c) => c.ruleId === '2.1');
    expect(bean).toBeDefined();
    expect(bean!.diagnosisSentence).toContain('no Spring stereotype');
    expect(bean!.fixSentence).toContain('@Service');
  });

  it('fires the LLM exactly once when no rule matched', async () => {
    let calls = 0;
    const llm: DiagnosisEngine = {
      diagnose: async (signal) => {
        calls++;
        return { ruleId: 'llm', phase: signal.phase, diagnosisSentence: 'guessed', fixSentence: 'try this', confidence: 'MEDIUM', excerpt: signal.rawExcerpt };
      },
    };
    const unknown = 'com.acme.TotallyUnknownException: weird thing happened\n\tat com.acme.Foo.bar(Foo.java:1)\n';
    const cards = await diagnoser().diagnoseAllEnriched(unknown, { llm });
    expect(calls).toBe(1);
    expect(cards.map((c) => c.ruleId)).toEqual(['llm']);
  });

  it('does not fire the LLM when a rule already matched', async () => {
    let calls = 0;
    const llm: DiagnosisEngine = { diagnose: async () => { calls++; return null; } };
    await diagnoser().diagnoseAllEnriched(NO_BEAN_LOG, { llm });
    expect(calls).toBe(0);
  });
});
