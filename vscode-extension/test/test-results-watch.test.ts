import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { afterEach, beforeEach, describe, it, expect } from 'vitest';
import { TestResultsWatcher } from '../src/capture/test-results-watch';
import { ConsoleDiagnoser } from '../src/engine';
import { loadCanonicalCatalog } from './helpers';

const FAILING_XML = `<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.OrderServiceTest" tests="1" failures="1" errors="0">
  <testcase name="loadsContext" classname="com.example.OrderServiceTest">
    <failure message="java.lang.IllegalStateException: Failed to load ApplicationContext">
java.lang.IllegalStateException: Failed to load ApplicationContext
Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.PricingClient' available
    </failure>
  </testcase>
</testsuite>`;

describe('TestResultsWatcher.pollOnce', () => {
  let root: string;
  let resultsDir: string;
  let watcher: TestResultsWatcher;

  beforeEach(() => {
    root = fs.mkdtempSync(path.join(os.tmpdir(), 'sbd-watch-'));
    resultsDir = path.join(root, 'app/build/test-results/test');
    fs.mkdirSync(resultsDir, { recursive: true });
    watcher = new TestResultsWatcher(new ConsoleDiagnoser(loadCanonicalCatalog()), () => root);
  });

  afterEach(() => {
    fs.rmSync(root, { recursive: true, force: true });
  });

  it('does not surface files present at baseline', () => {
    fs.writeFileSync(path.join(resultsDir, 'TEST-pre.xml'), FAILING_XML);
    watcher.baseline();
    expect(watcher.pollOnce()).toEqual([]);
  });

  it('diagnoses a result file written after baseline', () => {
    watcher.baseline();
    fs.writeFileSync(path.join(resultsDir, 'TEST-new.xml'), FAILING_XML);
    const cards = watcher.pollOnce();
    expect(cards.map((c) => c.ruleId)).toContain('2.1');
  });

  it('does not re-surface an unchanged file on the next poll', () => {
    watcher.baseline();
    fs.writeFileSync(path.join(resultsDir, 'TEST-new.xml'), FAILING_XML);
    expect(watcher.pollOnce().length).toBeGreaterThan(0);
    expect(watcher.pollOnce()).toEqual([]);
  });

  it('re-surfaces when the file is rewritten (re-run)', () => {
    watcher.baseline();
    const file = path.join(resultsDir, 'TEST-new.xml');
    fs.writeFileSync(file, FAILING_XML);
    expect(watcher.pollOnce().length).toBeGreaterThan(0);
    // Simulate a re-run rewriting the file with a newer mtime.
    const future = new Date(Date.now() + 5000);
    fs.writeFileSync(file, FAILING_XML);
    fs.utimesSync(file, future, future);
    expect(watcher.pollOnce().map((c) => c.ruleId)).toContain('2.1');
  });

  it('deduplicates the same failure across many suite files in one batch', () => {
    watcher.baseline();
    fs.writeFileSync(path.join(resultsDir, 'TEST-a.xml'), FAILING_XML);
    fs.writeFileSync(path.join(resultsDir, 'TEST-b.xml'), FAILING_XML);
    const cards = watcher.pollOnce();
    expect(cards.filter((c) => c.ruleId === '2.1')).toHaveLength(1);
  });
});
