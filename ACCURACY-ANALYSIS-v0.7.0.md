# Accuracy and Performance Analysis: v0.7.0

**Release theme:** a cloneable testbed app that triggers many rules at once, and the rule fixes
it surfaced.
**Date:** 2026-06-24

## 1. The testbed

`samples/testbed/` is a deliberately-broken Spring Boot 3.2 app. Running `./gradlew test` makes
it fail in eight different ways across context-load and runtime web errors. It exists so the
plugin can be verified against real IDE output in one run, and as a regression anchor.

Verified end to end: the real combined output of one testbed run, fed through the multi-error
path, yields exactly the rules the testbed targets:

| Test | Rule |
|---|---|
| MissingBeanContextTest | 2.1 |
| NoUniqueBeanContextTest | 2.2 |
| CircularDependencyContextTest | 1.13 |
| UnresolvedPlaceholderContextTest | 3.1 |
| AmbiguousMappingContextTest | 5.8 |
| RuntimeErrorsIT.validationFailure | 5.5 |
| RuntimeErrorsIT.noHandlerFound | 5.13 |
| RuntimeErrorsIT.jacksonRecursion | 7.2 |

That run's genuine output is committed as `TESTBED-combined-run.log` and asserted by
`TestbedCoverageTest`, so if any rule signal drifts away from real Spring output, CI fails.

## 2. Three real rule gaps the testbed found

Building and running a real app (not synthetic fixtures) exposed three signals that did not
match real Spring Boot 3.2 output:

- **2.2 (NoUniqueBeanDefinitionException)** keyed on "required a single bean, but" — the actual
  exception message is "expected single matching bean but found N". Fixed: keyed on the class.
- **5.1 (404)** keyed on the `NoHandlerFoundException` class, but Boot 3.2 logs "No endpoint GET
  /path." and the class name is absent from the log. Fixed: added **rule 5.13** for the modern
  phrasing (same 404 diagnosis).
- **7.2 (Jackson recursion)** keyed on `StackOverflowError` as the deepest cause, but the common
  web case reports "Could not write JSON: Infinite recursion (StackOverflowError)" via
  `HttpMessageNotWritableException`, so the StackOverflowError is not the deepest cause. Fixed:
  keyed on the "Infinite recursion" message.

## 3. Catalog and tests

- 57 rules, all DONE (added 5.13).
- 183 tests passing, including `TestbedCoverageTest` over the genuine multi-failure run.

## 4. Honest limitations

- The testbed deliberately does not cover compile-time rules (they would block its build),
  Kafka (needs a broker), or JPA/DataSource/Security rules (extra starters would change every
  test's context). These can be added as opt-in modules later.
- The engine logic is verified against the genuine output; the live tool-window rendering during
  a run is still worth a hands-on check (run the testbed in the IDE with the plugin installed).
