# Spring Debugger Testbed

A deliberately-broken Spring Boot 3.2 app that triggers many of the plugin's rules at once, so
you can verify the **Spring Boot Debugger** plugin against real IDE output in one run.

It is **meant to fail** — every test here drives the app down a different failure path. Run it
with the plugin installed and watch the **Spring Debugger** tool window fill with diagnoses.

## How to run

```
cd samples/testbed
./gradlew test            # runs everything; failures are expected
```

For the widest coverage, run it through the IDE so the plugin can observe the output:

- **Run via the Gradle tool window** (or a terminal `./gradlew test`) — the plugin's
  build/terminal tap reads the streamed output, which covers both the context-load failures and
  the runtime (web) errors.
- Running the context-load tests through the **IDE JUnit runner** also surfaces them (the test
  tap reads each failed test's stacktrace).

Then open the **Spring Debugger** tool window and check the grouped history. Report back which
rules show up.

## What it triggers (verified)

| Test | Rule | Error |
|---|---|---|
| `MissingBeanContextTest` | 2.1 | a bean needs an undefined collaborator |
| `NoUniqueBeanContextTest` | 2.2 | two beans of one type, consumer needs one |
| `CircularDependencyContextTest` | 1.13 | two beans require each other |
| `UnresolvedPlaceholderContextTest` | 3.1 | `@Value` for a property with no source/default |
| `AmbiguousMappingContextTest` | 5.8 | two handlers mapped to the same path |
| `RuntimeErrorsIT.validationFailure` | 5.5 | invalid `@RequestBody` |
| `RuntimeErrorsIT.noHandlerFound` | 5.13 | 404 (no endpoint) |
| `RuntimeErrorsIT.jacksonRecursion` | 7.2 | Jackson infinite recursion |

Building this testbed and running it found three real rule gaps that are now fixed: the
`NoUniqueBeanDefinitionException` message wording (2.2), the Spring Boot 3.2 "No endpoint" 404
phrasing (5.13), and Jackson recursion reported via `HttpMessageNotWritableException` (7.2).

## Not covered here (on purpose)

- **Compile-time rules** (Lombok 10.5, MapStruct 13.x, removed `WebSecurityConfigurerAdapter`
  6.4) — these require code that does not compile, which would block the whole build.
- **Kafka** (section 14) — needs a broker or broker misconfiguration.
- **JPA / DataSource / Security** rules — these need extra starters that change every test's
  context; kept out so the failures above stay isolated.

These can be added as separate opt-in modules later.
