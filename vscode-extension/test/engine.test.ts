import { describe, it, expect } from 'vitest';
import { ConsoleDiagnoser, segment } from '../src/engine';
import { loadCanonicalCatalog } from './helpers';

// Targeted engine unit tests mirroring the Java ConsoleDiagnoserTest and StackTraceSegmenterTest.
// The parity suite proves whole-corpus equivalence; these pin specific behaviours as documentation
// and guard against regressions that might still net out to the same golden by coincidence.

const diagnoser = new ConsoleDiagnoser(loadCanonicalCatalog());

describe('ConsoleDiagnoser.diagnoseAll', () => {
  it('surfaces every distinct error from a noisy log', () => {
    const log = [
      '2024-01-01 10:00:00 ERROR c.e.web : request failed',
      'org.springframework.web.servlet.NoHandlerFoundException: No handler found for GET /api/missing',
      '\tat org.springframework.web.servlet.DispatcherServlet.noHandlerFound(DispatcherServlet.java:1)',
      '2024-01-01 10:00:05 ERROR c.e.web : write failed',
      'org.springframework.dao.DataIntegrityViolationException: could not execute statement; constraint [uq_email]',
      '\tat org.springframework.orm.jpa.HibernateJpaDialect.convert(HibernateJpaDialect.java:1)',
      '2024-01-01 10:00:09 ERROR c.e.kafka : send failed',
      'org.apache.kafka.common.errors.TimeoutException: Failed to update metadata after 60000 ms.',
      '',
    ].join('\n');

    const ruleIds = diagnoser.diagnoseAll(log).map((c) => c.ruleId).sort();
    expect(ruleIds).toEqual(['14.1', '4.13', '5.1'].sort());
  });

  it('deduplicates the same error hit many times', () => {
    const oneError =
      'org.springframework.web.servlet.NoHandlerFoundException: No handler found for GET /api/x\n' +
      '\tat org.springframework.web.servlet.DispatcherServlet.noHandlerFound(DispatcherServlet.java:1)\n';
    const cards = diagnoser.diagnoseAll(oneError + oneError + oneError);
    expect(cards).toHaveLength(1);
    expect(cards[0].ruleId).toBe('5.1');
  });

  it('produces nothing for a clean log', () => {
    expect(diagnoser.diagnoseAll('Started DemoApplication in 3.1 seconds\n')).toHaveLength(0);
  });
});

describe('StackTraceSegmenter.segment', () => {
  it('returns empty for blank input', () => {
    expect(segment('')).toEqual([]);
    expect(segment('   \n  ')).toEqual([]);
  });

  it('keeps a single-error log as one block', () => {
    const single = 'java.lang.IllegalStateException: boom\n\tat com.example.Foo.bar(Foo.java:1)';
    expect(segment(single)).toEqual([single]);
  });

  it('splits two top-level exceptions into two blocks', () => {
    const text =
      'java.lang.IllegalStateException: one\n\tat a.b.C.x(C.java:1)\n' +
      'java.lang.RuntimeException: two\n\tat a.b.C.y(C.java:2)';
    const blocks = segment(text);
    expect(blocks).toHaveLength(2);
    expect(blocks[0]).toContain('IllegalStateException');
    expect(blocks[1]).toContain('RuntimeException');
  });
});
