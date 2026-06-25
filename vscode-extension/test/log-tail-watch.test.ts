import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { afterEach, beforeEach, describe, it, expect } from 'vitest';
import { LogTailWatcher } from '../src/capture/log-tail-watch';
import { ConsoleDiagnoser } from '../src/engine';
import { loadCanonicalCatalog } from './helpers';

// Mirrors the LogFileTailService behaviour: baseline at EOF, run-boundary slicing, per-run dedup,
// re-surfacing on a re-run, and truncation reset.

const RUN1 = [
  '2026-06-24T10:00:00 INFO 100 --- [main] c.e.App : Starting App using Java 21 with PID 100',
  '2026-06-24T10:00:01 ERROR 100 --- [main] o.s.b : Application run failed',
  'org.springframework.beans.factory.UnsatisfiedDependencyException: error creating bean',
  "Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.PricingClient' available",
  '',
].join('\n');

const RUN2_START = '2026-06-24T10:05:00 INFO 200 --- [main] c.e.App : Starting App using Java 21 with PID 200\n';
const RUN2_CLEAN = '2026-06-24T10:05:01 INFO 200 --- [main] c.e.App : Started App in 3s\n';

// A fresh, shorter run (fewer bytes than RUN1) so a rewrite triggers the length < offset reset.
const RUN_SHORT = [
  '2026-06-24T11:00:00 INFO 300 --- [main] c.e.App : Starting App using Java 21 with PID 300',
  "Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.PricingClient' available",
  '',
].join('\n');

describe('LogTailWatcher', () => {
  let dir: string;
  let logFile: string;
  let watcher: LogTailWatcher;

  beforeEach(() => {
    dir = fs.mkdtempSync(path.join(os.tmpdir(), 'sbd-tail-'));
    logFile = path.join(dir, 'app.log');
    fs.writeFileSync(logFile, '');
    watcher = new LogTailWatcher(new ConsoleDiagnoser(loadCanonicalCatalog()), () => [logFile]);
    watcher.baseline();
  });

  afterEach(() => fs.rmSync(dir, { recursive: true, force: true }));

  it('diagnoses an error appended after baseline', () => {
    fs.appendFileSync(logFile, RUN1);
    expect(watcher.pollOnce().map((c) => c.ruleId)).toContain('2.1');
  });

  it('does not re-surface a steady error within the same run', () => {
    fs.appendFileSync(logFile, RUN1);
    expect(watcher.pollOnce().length).toBeGreaterThan(0);
    // Same error logged again in the same run (no new Starting line).
    fs.appendFileSync(logFile, RUN1.split('\n').slice(1).join('\n'));
    expect(watcher.pollOnce()).toEqual([]);
  });

  it('ignores pre-baseline content (no replay on open)', () => {
    // Write before a fresh baseline; baseline should set the offset to EOF.
    fs.appendFileSync(logFile, RUN1);
    watcher.baseline();
    expect(watcher.pollOnce()).toEqual([]);
  });

  it('re-surfaces the same error on a re-run', () => {
    fs.appendFileSync(logFile, RUN1);
    expect(watcher.pollOnce().length).toBeGreaterThan(0);
    // A new run starts clean, then hits the same error again.
    fs.appendFileSync(logFile, RUN2_START + RUN1.split('\n').slice(1).join('\n'));
    expect(watcher.pollOnce().map((c) => c.ruleId)).toContain('2.1');
  });

  it('does not surface a stale error once a clean re-run has started', () => {
    fs.appendFileSync(logFile, RUN1);
    watcher.pollOnce();
    fs.appendFileSync(logFile, RUN2_START + RUN2_CLEAN);
    expect(watcher.pollOnce()).toEqual([]);
  });

  it('resets and re-reads on truncation', () => {
    fs.appendFileSync(logFile, RUN1);
    watcher.pollOnce();
    // Truncate (rotation) to a shorter fresh run; length < offset must trigger a reset and re-read.
    fs.writeFileSync(logFile, RUN_SHORT);
    expect(watcher.pollOnce().map((c) => c.ruleId)).toContain('2.1');
  });
});
