import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { afterAll, beforeAll, describe, it, expect } from 'vitest';
import { failureTexts, hasFailures } from '../src/engine/test-results-parser';
import { locate } from '../src/engine/test-results-locator';
import { ConsoleDiagnoser } from '../src/engine';
import { loadCanonicalCatalog } from './helpers';

// Mirrors Java TestResultsParserTest and TestResultsLocatorTest: the terminal-agnostic test path.

const XML = `<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.OrderServiceTest" tests="1" skipped="0" failures="1" errors="0" time="1.2">
  <testcase name="loadsContext" classname="com.example.OrderServiceTest" time="1.1">
    <failure message="java.lang.IllegalStateException: Failed to load ApplicationContext" type="java.lang.IllegalStateException">
java.lang.IllegalStateException: Failed to load ApplicationContext for [WebMergedContextConfiguration@1]
Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.PricingClient' available: expected at least 1 bean which qualifies as autowire candidate
	at org.springframework.beans.factory.support.DefaultListableBeanFactory.raiseNoMatchingBeanFound(DefaultListableBeanFactory.java:2144)
    </failure>
  </testcase>
</testsuite>`;

describe('TestResultsParser', () => {
  it('extracts the failure body from results XML', () => {
    const failures = failureTexts(XML);
    expect(failures).toHaveLength(1);
    expect(failures[0]).toContain('NoSuchBeanDefinitionException');
    expect(failures[0]).toContain('PricingClient');
  });

  it('extracted failure feeds the engine to a concrete diagnosis', () => {
    const failure = failureTexts(XML)[0];
    const cards = new ConsoleDiagnoser(loadCanonicalCatalog()).diagnoseAll(failure);
    expect(cards.map((c) => c.ruleId)).toContain('2.1');
  });

  it('a clean suite yields nothing', () => {
    const passing =
      '<testsuite name="x" tests="3" failures="0" errors="0"><testcase name="a"/></testsuite>';
    expect(hasFailures(passing)).toBe(false);
    expect(failureTexts(passing)).toEqual([]);
  });

  it('decodes entities in the failure message', () => {
    const xml = '<testsuite><failure message="expected &lt;1&gt; but was &lt;2&gt;">body</failure></testsuite>';
    expect(failureTexts(xml)[0]).toContain('expected <1> but was <2>');
  });
});

describe('TestResultsLocator', () => {
  let root: string;

  beforeAll(() => {
    root = fs.mkdtempSync(path.join(os.tmpdir(), 'sbd-locator-'));
    const gradle = path.join(root, 'app/build/test-results/test');
    fs.mkdirSync(gradle, { recursive: true });
    fs.writeFileSync(path.join(gradle, 'TEST-Foo.xml'), '<testsuite/>');
    const maven = path.join(root, 'svc/target/surefire-reports');
    fs.mkdirSync(maven, { recursive: true });
    fs.writeFileSync(path.join(maven, 'TEST-Bar.xml'), '<testsuite/>');
    const src = path.join(root, 'app/src/test/resources');
    fs.mkdirSync(src, { recursive: true });
    fs.writeFileSync(path.join(src, 'not-a-result.xml'), '<x/>');
  });

  afterAll(() => {
    fs.rmSync(root, { recursive: true, force: true });
  });

  it('finds Gradle and Maven results and prunes source', () => {
    const names = locate(root).map((f) => path.basename(f)).sort();
    expect(names).toEqual(['TEST-Bar.xml', 'TEST-Foo.xml']);
  });
});
