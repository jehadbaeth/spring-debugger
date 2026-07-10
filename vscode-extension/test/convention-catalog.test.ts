import { describe, it, expect } from 'vitest';
import { loadCanonicalConventions } from './helpers';
import { getCheck, hasCheck } from '../src/convention/check-registry';
import { appliesToFileType } from '../src/convention/convention-rule';

describe('ConventionCatalog (canonical conventions.yaml)', () => {
  const catalog = loadCanonicalConventions();

  it('loads every rule as DONE with a registered check implementation', () => {
    const rules = catalog.all();
    expect(rules.length).toBeGreaterThanOrEqual(14);
    for (const rule of rules) {
      expect(rule.status).toBe('DONE');
      expect(hasCheck(rule.checkType)).toBe(true);
      expect(getCheck(rule.checkType)).toBeDefined();
    }
  });

  it('restricts Robot rules to .robot files and Java rules to .java files', () => {
    for (const rule of catalog.all()) {
      if (rule.checkType.startsWith('robot')) {
        expect(appliesToFileType(rule, 'robot')).toBe(true);
        expect(appliesToFileType(rule, 'java')).toBe(false);
      } else {
        expect(appliesToFileType(rule, 'java')).toBe(true);
        expect(appliesToFileType(rule, 'robot')).toBe(false);
      }
    }
  });

  it('looks up a known rule by id', () => {
    const ids = catalog.all().map((r) => r.id);
    expect(ids.length).toBe(new Set(ids).size);
    const first = catalog.all()[0];
    expect(catalog.byId(first.id)).toEqual(first);
  });
});
