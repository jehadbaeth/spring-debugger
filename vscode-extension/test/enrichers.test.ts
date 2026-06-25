import { describe, it, expect } from 'vitest';
import {
  ActuatorEnricher,
  ClassFacts,
  DiagnosisCard,
  EnrichmentContext,
  PropertyPrecedenceEnricher,
  PsiEnricher,
  RawSignal,
  overallHealth,
  firstDownComponent,
  effectivePropertySource,
} from '../src/engine';

// Mirrors Java ActuatorReaderTest, ActuatorEnricherTest, PropertyPrecedenceEnricherTest, PsiEnricherTest.

function ctxWith(opts: Partial<EnrichmentContext>): EnrichmentContext {
  return {
    findClass: opts.findClass ?? (async () => null),
    springBootApplicationPackages: opts.springBootApplicationPackages ?? (async () => []),
    httpGet: opts.httpGet ?? (async () => null),
  };
}

describe('ActuatorReader', () => {
  it('reads overall status', () => {
    expect(overallHealth('{"status":"DOWN","components":{"db":{"status":"UP"}}}')).toBe('DOWN');
  });
  it('finds the first down component', () => {
    const body =
      '{"status":"DOWN","components":{"diskSpace":{"status":"UP"},"db":{"status":"DOWN","details":{"error":"x"}}}}';
    expect(firstDownComponent(body)).toBe('db');
  });
  it('no down component when all up', () => {
    expect(firstDownComponent('{"status":"UP","components":{"db":{"status":"UP"}}}')).toBeNull();
  });
  it('reads the effective property source', () => {
    const body =
      '{"property":{"source":"systemEnvironment","value":"prod"},"propertySources":[{"name":"systemEnvironment","property":{"value":"prod"}}]}';
    expect(effectivePropertySource(body)).toBe('systemEnvironment');
  });
  it('null bodies are empty', () => {
    expect(overallHealth(null)).toBeNull();
    expect(firstDownComponent(null)).toBeNull();
    expect(effectivePropertySource(null)).toBeNull();
  });
});

function runtimeCard(): DiagnosisCard {
  return { ruleId: '4.4', phase: 'RUNTIME', diagnosisSentence: 'Pool exhausted.', fixSentence: 'Fix the leak.', confidence: 'MEDIUM', excerpt: 'excerpt' };
}
function runtimeSignal(): RawSignal {
  return new RawSignal('RUNTIME', 'org.example.SomeException', 'boom', null, null, null, -1, ['boom'], 'boom');
}

describe('ActuatorEnricher', () => {
  const enricher = new ActuatorEnricher();
  it('down health sharpens and upgrades to HIGH', async () => {
    const ctx = ctxWith({ httpGet: async () => '{"status":"DOWN","components":{"db":{"status":"DOWN"}}}' });
    const out = await enricher.enrich(runtimeCard(), runtimeSignal(), ctx);
    expect(out.confidence).toBe('HIGH');
    expect(out.diagnosisSentence).toContain('DOWN');
    expect(out.diagnosisSentence).toContain('db');
  });
  it('health UP leaves the card unchanged', async () => {
    const ctx = ctxWith({ httpGet: async () => '{"status":"UP"}' });
    const inCard = runtimeCard();
    expect(await enricher.enrich(inCard, runtimeSignal(), ctx)).toBe(inCard);
  });
  it('unreachable app leaves the card unchanged', async () => {
    const ctx = ctxWith({ httpGet: async () => null });
    const inCard = runtimeCard();
    expect(await enricher.enrich(inCard, runtimeSignal(), ctx)).toBe(inCard);
  });
  it('startup phase is not probed', async () => {
    let called = false;
    const ctx = ctxWith({ httpGet: async () => { called = true; return null; } });
    const startup = new RawSignal('STARTUP', null, null, null, null, null, -1, [], 'x');
    const inCard = runtimeCard();
    expect(await enricher.enrich(inCard, startup, ctx)).toBe(inCard);
    expect(called).toBe(false);
  });
});

describe('PropertyPrecedenceEnricher', () => {
  const enricher = new PropertyPrecedenceEnricher();
  function placeholderSignal(key: string): RawSignal {
    const msg = `Could not resolve placeholder '${key}' in value`;
    return new RawSignal('RUNTIME', 'java.lang.IllegalArgumentException', msg, null, null, null, -1, [msg], msg);
  }
  function card(): DiagnosisCard {
    return { ruleId: '3.1', phase: 'RUNTIME', diagnosisSentence: 'A placeholder is unresolved.', fixSentence: 'Define it.', confidence: 'MEDIUM', excerpt: 'excerpt' };
  }
  it('appends the effective source when env knows the key', async () => {
    const ctx = ctxWith({
      httpGet: async (url) =>
        url === '/actuator/env/app.timeout'
          ? '{"property":{"source":"systemEnvironment","value":"5s"},"propertySources":[{"name":"systemEnvironment","property":{"value":"5s"}}]}'
          : null,
    });
    const out = await enricher.enrich(card(), placeholderSignal('app.timeout'), ctx);
    expect(out.diagnosisSentence).toContain('app.timeout');
    expect(out.diagnosisSentence).toContain('systemEnvironment');
  });
  it('no key leaves the card unchanged', async () => {
    const ctx = ctxWith({ httpGet: async () => null });
    const noKey = new RawSignal('RUNTIME', 'X', 'nothing dotted here', null, null, null, -1, [], 'x');
    const inCard = card();
    expect(await enricher.enrich(inCard, noKey, ctx)).toBe(inCard);
  });
  it('startup phase is not probed', async () => {
    let called = false;
    const ctx = ctxWith({ httpGet: async () => { called = true; return null; } });
    const startup = new RawSignal('STARTUP', 'X', 'spring.datasource.url missing', null, null, null, -1, [], 'x');
    const inCard = card();
    expect(await enricher.enrich(inCard, startup, ctx)).toBe(inCard);
    expect(called).toBe(false);
  });
});

describe('PsiEnricher', () => {
  const enricher = new PsiEnricher();
  function facts(fqn: string, isInterface: boolean, annotations: string[], inProjectSource: boolean): ClassFacts {
    const simple = fqn.substring(fqn.lastIndexOf('.') + 1);
    const pkg = fqn.includes('.') ? fqn.substring(0, fqn.lastIndexOf('.')) : '';
    return new ClassFacts(fqn, pkg, isInterface, new Set(annotations), true, inProjectSource, simple + '.java');
  }
  function context(table: Record<string, ClassFacts>, roots: string[]): EnrichmentContext {
    return ctxWith({
      findClass: async (name) => {
        const simple = name.includes('.') ? name.substring(name.lastIndexOf('.') + 1) : name;
        return table[name] ?? table[simple] ?? null;
      },
      springBootApplicationPackages: async () => roots,
    });
  }
  function signal(message: string, failingBean: string | null): RawSignal {
    return new RawSignal('STARTUP', 'org.springframework.beans.factory.NoSuchBeanDefinitionException', message, null, null, failingBean, -1, [message], message);
  }
  function card(ruleId: string): DiagnosisCard {
    return { ruleId, phase: 'STARTUP', diagnosisSentence: 'offline diagnosis', fixSentence: 'offline fix', confidence: 'HIGH', excerpt: 'excerpt' };
  }

  it('mapper interface names mapper and componentModel', async () => {
    const ctx = context({ UserMapper: facts('com.example.UserMapper', true, ['Mapper'], true) }, ['com.example']);
    const out = await enricher.enrich(card('13.4'), signal("No qualifying bean of type 'com.example.UserMapper' available", null), ctx);
    expect(out.diagnosisSentence).toContain('com.example.UserMapper');
    expect(out.diagnosisSentence).toContain('@Mapper');
    expect(out.fixSentence).toContain('componentModel');
    expect(out.fixSentence).toContain('UserMapper.java');
  });
  it('missing stereotype on a service picks @Service and names file and consumer', async () => {
    const ctx = context({ OrderService: facts('com.example.OrderService', false, [], true) }, ['com.example']);
    const out = await enricher.enrich(card('2.1'), signal("No qualifying bean of type 'com.example.OrderService' available", 'checkoutController'), ctx);
    expect(out.diagnosisSentence).toContain('com.example.OrderService');
    expect(out.diagnosisSentence).toContain('no Spring stereotype');
    expect(out.diagnosisSentence).toContain("required by bean 'checkoutController'");
    expect(out.fixSentence).toContain('@Service');
    expect(out.fixSentence).toContain('OrderService.java');
  });
  it('missing stereotype on a repository picks @Repository', async () => {
    const ctx = context({ CustomerRepository: facts('com.example.CustomerRepository', false, [], true) }, ['com.example']);
    const out = await enricher.enrich(card('2.1'), signal("No qualifying bean of type 'com.example.CustomerRepository' available", null), ctx);
    expect(out.fixSentence).toContain('@Repository');
  });
  it('third-party type recommends an @Bean method', async () => {
    const ctx = context({ RestTemplate: facts('org.springframework.web.client.RestTemplate', false, [], false) }, ['com.example']);
    const out = await enricher.enrich(card('2.1'), signal("No qualifying bean of type 'org.springframework.web.client.RestTemplate' available", null), ctx);
    expect(out.diagnosisSentence).toContain('library');
    expect(out.fixSentence).toContain('@Bean');
    expect(out.fixSentence).toContain('restTemplate()');
  });
  it('project interface tells the user to annotate the implementation', async () => {
    const ctx = context({ PaymentGateway: facts('com.example.PaymentGateway', true, [], true) }, ['com.example']);
    const out = await enricher.enrich(card('2.1'), signal("No qualifying bean of type 'com.example.PaymentGateway' available", null), ctx);
    expect(out.diagnosisSentence).toContain('interface');
    expect(out.fixSentence).toContain('implements');
  });
  it('annotated class outside the scan tree reports @ComponentScan', async () => {
    const ctx = context({ PaymentService: facts('com.other.PaymentService', false, ['Service'], true) }, ['com.example']);
    const out = await enricher.enrich(card('2.1'), signal("No qualifying bean of type 'com.other.PaymentService' available", null), ctx);
    expect(out.diagnosisSentence).toContain('outside the package tree');
    expect(out.fixSentence).toContain('@ComponentScan');
    expect(out.fixSentence).toContain('com.other');
  });
  it('unknown class leaves the card unchanged', async () => {
    const ctx = context({}, ['com.example']);
    const inCard = card('2.1');
    expect(await enricher.enrich(inCard, signal("'com.example.Ghost' available", null), ctx)).toBe(inCard);
  });
  it('non-target rule is left unchanged', async () => {
    const ctx = context({ Foo: facts('com.example.Foo', false, [], true) }, ['com.example']);
    const inCard = card('4.8');
    expect(await enricher.enrich(inCard, signal("'com.example.Foo'", null), ctx)).toBe(inCard);
  });
  it('annotated class inside the scan tree is not flagged', async () => {
    const ctx = context({ Ok: facts('com.example.web.Ok', false, ['Service'], true) }, ['com.example']);
    const inCard = card('2.1');
    expect(await enricher.enrich(inCard, signal("'com.example.web.Ok'", null), ctx)).toBe(inCard);
  });
});
