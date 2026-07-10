import { describe, it, expect } from 'vitest';
import { ConventionRule } from '../src/convention/convention-rule';
import { RobotMetadataRequiredCheck } from '../src/convention/checks/robot-metadata-required';
import { RobotTestIdFormatCheck } from '../src/convention/checks/robot-test-id-format';
import { RobotTestCaseDocCheck } from '../src/convention/checks/robot-test-case-doc';
import { RobotTestCaseTagsCheck } from '../src/convention/checks/robot-test-case-tags';

function rule(checkType: string, overrides: Partial<ConventionRule> = {}): ConventionRule {
  return {
    id: checkType,
    name: null,
    checkType,
    enabled: true,
    severity: 'WARNING',
    appliesTo: ['robot'],
    params: null,
    message: 'message {{field}}{{value}}{{test}}',
    fix: null,
    status: 'DONE',
    ...overrides,
  };
}

function suite(settingsBody: string, testCasesBody: string): string {
  return `*** Settings ***\n${settingsBody}\n*** Test Cases ***\n${testCasesBody}`;
}

describe('RobotMetadataRequiredCheck', () => {
  const check = new RobotMetadataRequiredCheck();
  const r = rule('robotMetadataRequired');

  it('flags each missing required metadata entry', () => {
    const text = suite('Metadata    Test ID    T-ST-0001\n', 'T\n    Log    hi\n');
    const out = check.check(text, 'suite.robot', r);
    expect(out).toHaveLength(2);
    const combined = out.map((v) => v.message).join(' ');
    expect(combined).toContain('Test Description');
    expect(combined).toContain('Pass-Fail Criteria');
  });

  it('is clean when all required metadata is present', () => {
    const text = suite(
      'Metadata    Test ID    T-ST-0001\nMetadata    Test Description    desc\nMetadata    Pass-Fail Criteria    crit\n',
      'T\n    Log    hi\n',
    );
    expect(check.check(text, 'suite.robot', r)).toHaveLength(0);
  });

  it('does not flag a resource file with no Test Cases section', () => {
    const text = '*** Keywords ***\nMy Keyword\n    Log    reusable\n';
    expect(check.check(text, 'keywords.robot', r)).toHaveLength(0);
  });
});

describe('RobotTestIdFormatCheck', () => {
  const check = new RobotTestIdFormatCheck();
  // Scope restricted to RE/FG/CA per conventions.yaml's team-confirmed idPattern override; the
  // check's own built-in default (T-(SYS|ST|CA|FG|RE)-\d{4}) only applies when a rule omits the
  // param, which the canonical catalog never does.
  const r = rule('robotTestIdFormat', { params: { idPattern: 'T-(RE|FG|CA)-\\d{4}' } });

  it('flags a test id outside the configured scope', () => {
    const text = suite('Metadata    Test ID    T-API-1909\n', 'T\n    Log    hi\n');
    const out = check.check(text, 'suite.robot', r);
    expect(out).toHaveLength(1);
    expect(out[0].message).toContain('T-API-1909');
  });

  it('passes a compliant test id (FG in scope)', () => {
    const text = suite('Metadata    Test ID    T-FG-0004\n', 'T\n    Log    hi\n');
    expect(check.check(text, 'suite.robot', r)).toHaveLength(0);
  });

  it('flags a test id whose scope is not RE/FG/CA', () => {
    const text = suite('Metadata    Test ID    T-ST-0001\n', 'T\n    Log    hi\n');
    expect(check.check(text, 'suite.robot', r)).toHaveLength(1);
  });
});

describe('RobotTestCaseDocCheck', () => {
  const check = new RobotTestCaseDocCheck();
  const r = rule('robotTestCaseDoc');

  it('flags only the test case missing [Documentation]', () => {
    const text = suite(
      'Metadata    Test ID    T-ST-0001\n',
      'Has Doc\n    [Documentation]    ok\n    Log    hi\n\nNo Doc\n    Log    hi\n',
    );
    const out = check.check(text, 'suite.robot', r);
    expect(out).toHaveLength(1);
    expect(out[0].message).toContain('No Doc');
  });
});

describe('RobotTestCaseTagsCheck', () => {
  const check = new RobotTestCaseTagsCheck();
  const r = rule('robotTestCaseTags');

  it('flags only the test case missing a REQ- tag', () => {
    const text = suite(
      'Metadata    Test ID    T-ST-0001\n',
      'Linked\n    [Tags]    REQ-1\n    Log    hi\n\nUnlinked\n    [Tags]    smoke\n    Log    hi\n',
    );
    const out = check.check(text, 'suite.robot', r);
    expect(out).toHaveLength(1);
    expect(out[0].message).toContain('Unlinked');
  });
});
