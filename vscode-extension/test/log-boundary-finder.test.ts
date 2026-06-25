import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { afterAll, beforeAll, describe, it, expect } from 'vitest';
import { isRunStart, lastRunSlice, fromProperties, fromYaml, discoverAll } from '../src/engine';
import { ConsoleDiagnoser } from '../src/engine';
import { loadCanonicalCatalog } from './helpers';

// Mirrors Java LogRunBoundaryTest and LogFilePropertyFinderTest.

const TWO_RUNS = [
  '2026-06-24T10:00:00 INFO 100 --- [main] c.e.App : Starting App using Java 21 with PID 100',
  '2026-06-24T10:00:01 ERROR 100 --- [main] o.h.e.jdbc : Connection to localhost:5432 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.',
  '2026-06-24T10:00:02 INFO 100 --- [main] c.e.App : Started App',
  '2026-06-24T10:05:00 INFO 200 --- [main] c.e.App : Starting App using Java 21 with PID 200',
  '2026-06-24T10:05:01 INFO 200 --- [main] c.e.App : Started App in 3s',
  '',
].join('\n');

describe('LogRunBoundary', () => {
  it('detects a run start line', () => {
    expect(isRunStart('2026 INFO --- c.e.App : Starting App using Java 21 with PID 100')).toBe(true);
    expect(isRunStart('2026 INFO --- c.e.App : Started App')).toBe(false);
  });

  it('lastRunSlice drops the earlier run', () => {
    const slice = lastRunSlice(TWO_RUNS);
    expect(slice).toContain('with PID 200');
    expect(slice).toContain('Started App in 3s');
    expect(slice).not.toContain('PID 100');
    expect(slice).not.toContain('Connection to localhost:5432 refused');
  });

  it('does not diagnose a stale error from a previous run', () => {
    const slice = lastRunSlice(TWO_RUNS);
    const cards = new ConsoleDiagnoser(loadCanonicalCatalog()).diagnoseAll(slice);
    expect(cards.map((c) => c.ruleId)).not.toContain('4.15');
  });

  it('returns the whole text when there is no marker', () => {
    const plain = 'just some output\nwith no spring boot start line\n';
    expect(lastRunSlice(plain)).toBe(plain);
  });
});

describe('LogFilePropertyFinder', () => {
  it('reads logging.file.name from properties', () => {
    const props =
      'spring.application.name=fg-analysis\nlogging.file.name=build/logs/fg-analysis.log\nserver.port=9351\n';
    expect(fromProperties(props)).toBe('build/logs/fg-analysis.log');
  });

  it('reads nested logging.file.name from yaml', () => {
    const y = 'logging:\n  file:\n    name: build/logs/app.log\nserver:\n  port: 8080\n';
    expect(fromYaml(y)).toBe('build/logs/app.log');
  });

  it('reads a flattened yaml key', () => {
    expect(fromYaml('logging.file.name: out/app.log')).toBe('out/app.log');
  });

  it('returns null for a console-only config', () => {
    expect(fromProperties('server.port=9351\nspring.profiles.active=local')).toBeNull();
    expect(fromYaml('server:\n  port: 8080')).toBeNull();
  });

  describe('discoverAll', () => {
    let root: string;
    beforeAll(() => {
      root = fs.mkdtempSync(path.join(os.tmpdir(), 'sbd-finder-'));
      for (const [svc, body] of [
        ['svc-a', 'logging.file.name=build/a.log\n'],
        ['svc-b', 'logging.file.name=build/b.log\n'],
        ['svc-c', 'server.port=8083\n'],
      ] as const) {
        const res = path.join(root, svc, 'src/main/resources');
        fs.mkdirSync(res, { recursive: true });
        fs.writeFileSync(path.join(res, 'application.properties'), body);
      }
    });
    afterAll(() => fs.rmSync(root, { recursive: true, force: true }));

    it('resolves one log per service against its module, skipping console-only', () => {
      const rel = discoverAll(root)
        .map((p) => p.replace(root, '').split(path.sep).join('/'))
        .sort();
      expect(rel).toEqual(['/svc-a/build/a.log', '/svc-b/build/b.log']);
    });
  });
});
