import { describe, it, expect } from 'vitest';
import { ConventionRule } from '../src/convention/convention-rule';
import { Violation } from '../src/convention/violation';
import { JavadocRequiredCheck } from '../src/convention/checks/javadoc-required';
import { FieldInjectionForbiddenCheck } from '../src/convention/checks/field-injection-forbidden';
import { NoSystemOutErrCheck } from '../src/convention/checks/no-system-out-err';
import { OptionalUsageCheck } from '../src/convention/checks/optional-usage';
import { TransactionalMisplacedCheck } from '../src/convention/checks/transactional-misplaced';
import { EntityStringFieldBoundedCheck } from '../src/convention/checks/entity-string-field-bounded';
import { RequestBodyRequiresValidCheck } from '../src/convention/checks/request-body-requires-valid';
import { ServiceClassNamingCheck } from '../src/convention/checks/service-class-naming';
import { ApiVersionPathCheck } from '../src/convention/checks/api-version-path';

function rule(checkType: string, overrides: Partial<ConventionRule> = {}): ConventionRule {
  return {
    id: checkType,
    name: null,
    checkType,
    enabled: true,
    severity: 'WARNING',
    appliesTo: ['java'],
    params: null,
    message: 'message {{x}}',
    fix: null,
    status: 'DONE',
    ...overrides,
  };
}

function anchors(text: string, violations: Violation[]): string[] {
  return violations.map((v) => text.substring(v.range.start, v.range.end));
}

describe('JavadocRequiredCheck', () => {
  const check = new JavadocRequiredCheck();
  const r = rule('javadocRequired');

  it('flags a public method without javadoc', () => {
    const text = 'public class Sample { public void doWork() {} }';
    const out = check.check(text, 'Sample.java', r);
    expect(anchors(text, out)).toEqual(['doWork']);
  });

  it('does not flag a public method with javadoc', () => {
    const text = 'public class Sample { /** Does the work. */ public void doWork() {} }';
    expect(check.check(text, 'Sample.java', r)).toHaveLength(0);
  });

  it('does not flag private or package-private methods', () => {
    const text = 'public class Sample { private void a() {} void b() {} }';
    expect(check.check(text, 'Sample.java', r)).toHaveLength(0);
  });

  it('exempts @Override methods', () => {
    const text = 'public class Sample { @Override public String toString() { return ""; } }';
    expect(check.check(text, 'Sample.java', r)).toHaveLength(0);
  });

  it('exempts getters, setters and boolean is-accessors', () => {
    const text =
      'public class Sample { private int x; public int getX() { return x; } public void setX(int x) { this.x = x; } public boolean isReady() { return true; } }';
    expect(check.check(text, 'Sample.java', r)).toHaveLength(0);
  });

  it('never flags a constructor', () => {
    const text = 'public class Sample { public Sample() {} }';
    expect(check.check(text, 'Sample.java', r)).toHaveLength(0);
  });

  it('reports every offending method', () => {
    const text = 'public class Sample { public void first() {} public void second() {} }';
    const out = check.check(text, 'Sample.java', r);
    expect(anchors(text, out)).toEqual(['first', 'second']);
  });
});

describe('FieldInjectionForbiddenCheck', () => {
  const check = new FieldInjectionForbiddenCheck();
  const r = rule('fieldInjectionForbidden');

  it('flags a field with an injection annotation', () => {
    const text = 'public class Sample { @Autowired private Object repo; }';
    const out = check.check(text, 'Sample.java', r);
    expect(anchors(text, out)).toEqual(['repo']);
  });

  it('does not flag a plain field', () => {
    const text = 'public class Sample { private Object repo; }';
    expect(check.check(text, 'Sample.java', r)).toHaveLength(0);
  });
});

describe('NoSystemOutErrCheck', () => {
  const check = new NoSystemOutErrCheck();
  const r = rule('noSystemOutErr');

  it('flags System.out usage', () => {
    const text = 'public class Sample { void run() { System.out.println("hi"); } }';
    const out = check.check(text, 'Sample.java', r);
    expect(anchors(text, out)).toEqual(['System.out']);
  });

  it('flags System.err usage', () => {
    const text = 'public class Sample { void run() { System.err.println("hi"); } }';
    const out = check.check(text, 'Sample.java', r);
    expect(anchors(text, out)).toEqual(['System.err']);
  });

  it('does not flag unrelated calls', () => {
    const text = 'public class Sample { void run() { logger.info("hi"); } }';
    expect(check.check(text, 'Sample.java', r)).toHaveLength(0);
  });
});

describe('OptionalUsageCheck', () => {
  const check = new OptionalUsageCheck();

  it('flags an Optional parameter by default', () => {
    const text = 'public class Sample { void handle(Optional<String> id) {} }';
    const out = check.check(text, 'Sample.java', rule('optionalUsage'));
    expect(anchors(text, out)).toEqual(['id']);
  });

  it('flags an Optional field when target=field', () => {
    const text = 'public class Sample { private Optional<String> id; }';
    const out = check.check(text, 'Sample.java', rule('optionalUsage', { params: { target: 'field' } }));
    expect(anchors(text, out)).toEqual(['id']);
  });

  it('does not flag a non-Optional parameter', () => {
    const text = 'public class Sample { void handle(String id) {} }';
    expect(check.check(text, 'Sample.java', rule('optionalUsage'))).toHaveLength(0);
  });
});

describe('TransactionalMisplacedCheck', () => {
  const check = new TransactionalMisplacedCheck();
  const r = rule('transactionalMisplaced');

  it('flags @Transactional on a method inside an excluded-marker class', () => {
    const text = '@Repository public class Sample { @Transactional void run() {} }';
    const out = check.check(text, 'Sample.java', r);
    expect(anchors(text, out)).toEqual(['@Transactional']);
  });

  it('flags @Transactional on the excluded-marker class itself', () => {
    const text = '@RestController @Transactional public class Sample {}';
    const out = check.check(text, 'Sample.java', r);
    expect(anchors(text, out)).toEqual(['@Transactional']);
  });

  it('does not flag @Transactional in a plain (non-excluded) class', () => {
    const text = 'public class Sample { @Transactional void run() {} }';
    expect(check.check(text, 'Sample.java', r)).toHaveLength(0);
  });
});

describe('EntityStringFieldBoundedCheck', () => {
  const check = new EntityStringFieldBoundedCheck();
  const r = rule('entityStringFieldBounded');

  it('flags an unbounded String field on an @Entity class', () => {
    const text = '@Entity public class Sample { private String name; }';
    const out = check.check(text, 'Sample.java', r);
    expect(anchors(text, out)).toEqual(['name']);
  });

  it('does not flag a bounded String field', () => {
    const text = '@Entity public class Sample { @Column(length = 50) private String name; }';
    expect(check.check(text, 'Sample.java', r)).toHaveLength(0);
  });

  it('does not flag a String field outside an @Entity class', () => {
    const text = 'public class Sample { private String name; }';
    expect(check.check(text, 'Sample.java', r)).toHaveLength(0);
  });
});

describe('RequestBodyRequiresValidCheck', () => {
  const check = new RequestBodyRequiresValidCheck();
  const r = rule('requestBodyRequiresValid');

  it('flags @RequestBody without @Valid/@Validated', () => {
    const text = 'public class Sample { void handle(@RequestBody Object dto) {} }';
    const out = check.check(text, 'Sample.java', r);
    expect(anchors(text, out)).toEqual(['dto']);
  });

  it('does not flag @RequestBody with @Valid', () => {
    const text = 'public class Sample { void handle(@Valid @RequestBody Object dto) {} }';
    expect(check.check(text, 'Sample.java', r)).toHaveLength(0);
  });
});

describe('ServiceClassNamingCheck', () => {
  const check = new ServiceClassNamingCheck();
  const r = rule('serviceClassNaming');

  it('flags a @Service class not ending in "Service"', () => {
    const text = '@Service public class Screening {}';
    const out = check.check(text, 'Sample.java', r);
    expect(anchors(text, out)).toEqual(['Screening']);
  });

  it('does not flag a compliant @Service class name', () => {
    const text = '@Service public class ScreeningService {}';
    expect(check.check(text, 'Sample.java', r)).toHaveLength(0);
  });
});

describe('ApiVersionPathCheck', () => {
  const check = new ApiVersionPathCheck();
  const r = rule('apiVersionPath');

  it('flags an unversioned literal controller path', () => {
    const text = '@RequestMapping("/propagation") public class Sample {}';
    const out = check.check(text, 'Sample.java', r);
    expect(anchors(text, out)).toEqual(['@RequestMapping("/propagation")']);
  });

  it('does not flag a compliant versioned path', () => {
    const text = '@RequestMapping("/api/v1/orders") public class Sample {}';
    expect(check.check(text, 'Sample.java', r)).toHaveLength(0);
  });

  it('silently skips a non-literal path (constant reference)', () => {
    const text = '@RequestMapping(Sample.PATH) public class Sample {}';
    expect(check.check(text, 'Sample.java', r)).toHaveLength(0);
  });
});
