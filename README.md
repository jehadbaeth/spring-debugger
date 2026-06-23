# Spring Boot Debugger — IntelliJ Plugin

A zero-config IntelliJ plugin that watches your Spring Boot run, test, and build output and instantly surfaces a two-sentence diagnosis when an error is detected: one sentence naming the problem, one sentence giving the fix.

No reading cascading stack traces. No Googling the exception class name.

---

## Features

- **Real-time detection** — attaches to IntelliJ's run, test, and build streams; no polling, no file watching
- **43 rules** covering the most common Spring Boot errors across startup, runtime, test, and compile phases
- **Three-layer signal extraction** — reads `Caused by:` chains, failure analysis banners, and build error lines
- **Diagnosis history** — every diagnosis is stored per session; double-click any entry to re-inspect it
- **Copy Fix / Copy Diagnosis** — one-click clipboard for both sentences
- **Settings panel** — minimum confidence threshold, history size, balloon notifications, tool window focus
- **Phase-aware matching** — COMPILE rules do not fire on STARTUP logs; RUNTIME rules do not fire on TEST logs
- **Offline by design** — no network calls, no telemetry, no API keys

---

## Screenshots

### Tool window — diagnosis card

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Spring Debugger                                            ⚙           │
│  ● Monitoring  ·  43 rules                                              │
├─────────────────────────────────────────────────────────────────────────┤
│  [2.1]  STARTUP  ●HIGH                                                  │
│                                                                         │
│  Diagnosis                                                              │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │ Spring cannot find a bean of the required type because no class   │  │
│  │ with that type is registered in the application context.          │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│  Fix                                                                    │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │ Annotate the class with @Service, @Component, or @Repository, or │  │
│  │ declare it as an @Bean; confirm it lives inside the package tree  │  │
│  │ rooted at your @SpringBootApplication class.                      │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│  [Copy Fix]  [Copy Diagnosis]                                           │
├─────────────────────────────────────────────────────────────────────────┤
│  History                                                                │
│  [2.1] STARTUP  Spring cannot find a bean of the required type…         │
│  [4.2] STARTUP  DataSource not configured; add DB URL or H2…           │
│  [1.10] TEST    Test application context failed to load; read…          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Settings panel — Settings → Tools → Spring Boot Debugger

```
Spring Boot Debugger Settings
──────────────────────────────────────────────────────────────────────────

General
  ☑  Enable Spring Boot Debugger
  ☑  Show notification balloon on detection
  ☑  Focus tool window on error

Analysis
  Minimum confidence level:  [ MEDIUM ▾ ]
  Maximum history entries:   [ 30      ▾ ]

LLM Fallback (coming in a future release)
  ☐  Enable Ollama LLM fallback (disabled — not yet implemented)
  Ollama base URL:  [ http://localhost:11434  ]  (greyed out)
  Ollama model:     [ llama3                 ]  (greyed out)
```

---

## Supported Error Types

### Section 1 — Application context and startup (10 rules)

| Rule | Error | Signal |
|---|---|---|
| 1.1 | Unable to start web server | Banner: "Unable to start web server" |
| 1.2 | BeanDefinitionStoreException | Caused by: BeanDefinitionStoreException |
| 1.3 | BeanCreationException | Caused by: BeanCreationException + "Error creating bean with name" |
| 1.4 | BeanInstantiationException | Caused by: BeanInstantiationException + "No default constructor found" |
| 1.5 | BeanDefinitionOverrideException | Caused by: BeanDefinitionOverrideException + "could not be registered" |
| 1.8 | Port already in use | Banner: "Port" + "already in use" |
| 1.9 | No active profile | "No active profile set, falling back to default profiles" |
| 1.10 | Failed to load ApplicationContext (tests) | "Failed to load ApplicationContext" |
| 1.12 | Auto-configuration not applied | "UserDetailsService bean" |
| 1.13 | BeanCurrentlyInCreationException | Caused by: BeanCurrentlyInCreationException |

### Section 2 — Dependency injection (4 rules)

| Rule | Error | Signal |
|---|---|---|
| 2.1 | NoSuchBeanDefinitionException | Caused by: NoSuchBeanDefinitionException + "required a bean of type" |
| 2.2 | NoUniqueBeanDefinitionException | Caused by: NoUniqueBeanDefinitionException + "required a single bean, but" |
| 2.3 | UnsatisfiedDependencyException | Caused by: UnsatisfiedDependencyException |
| 2.7 | Circular dependency | "The dependencies of some of the beans in the application context form a cycle" |

### Section 3 — Configuration and properties (3 rules)

| Rule | Error | Signal |
|---|---|---|
| 3.1 | Could not resolve @Value placeholder | Caused by: IllegalArgumentException + "Could not resolve placeholder" |
| 3.4 | Property type conversion failure | "Failed to bind properties" |
| 3.5 | YAML syntax error | Caused by: ScannerException |

### Section 4 — JPA / data access (6 rules)

| Rule | Error | Signal |
|---|---|---|
| 4.1 | No embedded database driver | Banner: "Cannot determine embedded database driver class" |
| 4.2 | DataSource not configured | Banner: "Failed to configure a DataSource" |
| 4.4 | HikariCP pool exhausted | Caused by: SQLTransientConnectionException + "Connection is not available, request timed out" |
| 4.6 | Entity not a managed type | Caused by: IllegalArgumentException + "Not a managed type" |
| 4.8 | LazyInitializationException | Caused by: LazyInitializationException + "could not initialize proxy - no Session" |
| 4.13 | DataIntegrityViolationException | Caused by: DataIntegrityViolationException |
| 4.14 | RedisConnectionFactory not configured | Caused by: IllegalStateException + "RedisConnectionFactory is required" |

### Section 5 — Web, REST, and MVC (3 rules)

| Rule | Error | Signal |
|---|---|---|
| 5.1 | 404 No handler found | Caused by: NoHandlerFoundException |
| 5.5 | Bean validation failure | Caused by: MethodArgumentNotValidException |
| 5.8 | Ambiguous handler mapping | "Ambiguous mapping. Cannot map" |

### Section 6 — Spring Security (3 rules)

| Rule | Error | Signal |
|---|---|---|
| 6.2 | Default generated security password | "Using generated security password" |
| 6.4 | WebSecurityConfigurerAdapter removed | Build line: "WebSecurityConfigurerAdapter" |
| 6.5 | No PasswordEncoder mapped | "There is no PasswordEncoder mapped for the id" |

### Section 7 — Jackson / serialization (2 rules)

| Rule | Error | Signal |
|---|---|---|
| 7.2 | Infinite recursion on bidirectional JPA | Caused by: StackOverflowError + "Infinite recursion" |
| 7.4 | Cannot construct instance | Caused by: InvalidDefinitionException + "no Creators, like default constructor" |

### Section 9 — Testing (2 rules)

| Rule | Error | Signal |
|---|---|---|
| 9.2 | UnnecessaryStubbingException | Caused by: UnnecessaryStubbingException |
| 9.6 | Testcontainers Docker not available | "Could not find a valid Docker environment" |

### Section 10 — Build, packaging, classpath (3 rules)

| Rule | Error | Signal |
|---|---|---|
| 10.1 | NoSuchMethodError / version conflict | Caused by: NoSuchMethodError (fires in STARTUP, RUNTIME, and TEST) |
| 10.5 | Lombok not generating code | Build line: "cannot find symbol" |
| 10.6 | Java version mismatch | Caused by: UnsupportedClassVersionError |

### Section 13 — MapStruct (5 rules active)

| Rule | Error | Signal |
|---|---|---|
| 13.1 | Unmapped target properties | Build line: "Unmapped target" |
| 13.2 | Incompatible types in @Mapping | Build line: "Caused by" (build context) |
| 13.3 | Missing mapper implementation | Build line: "MapperImpl" |
| 13.4 | Mapper not a Spring bean | Caused by: NoSuchBeanDefinitionException + Caused-by message contains "Mapper" |
| 13.5 | No implementation type | Build line: "No implementation type is registered for return type" |
| 13.6 | Missing @Mapper annotation | Build line: containing annotation check |

---

## Installation

### From a release ZIP

1. Download `spring-debugger-<version>.zip` from the [Releases](https://github.com/jehadbaeth/spring-debugger/releases) page.
2. Open IntelliJ IDEA.
3. Go to **Settings → Plugins → ⚙ gear icon → Install Plugin from Disk…**
4. Select the downloaded ZIP and click **OK**.
5. Restart when prompted.

### From source

```bash
git clone https://github.com/jehadbaeth/spring-debugger.git
cd spring-debugger
./gradlew buildPlugin
# ZIP is at build/distributions/spring-debugger-<version>.zip
```

**Requirements:** IntelliJ IDEA Community or Ultimate 2023.3+

---

## Usage

Once installed the plugin monitors automatically. No setup is needed.

### Running your Spring Boot application

Run with **Shift+F10** or the Run button. If startup fails:

1. The **Spring Debugger** panel activates (bottom of the IDE, next to Run and Problems).
2. The diagnosis card appears at the top showing rule ID, phase, confidence, diagnosis, and fix.
3. Click **Copy Fix** to put the fix sentence on the clipboard.

### Running Spring Boot tests

Run tests normally. If the test application context fails to build, the same card appears for missing beans, circular dependencies, missing mocks, and similar errors.

### Reading the history

Every diagnosis above the minimum confidence threshold is added to the history list below the card. Double-click any entry to re-inspect it in the card view.

### Settings

Open **Settings → Tools → Spring Boot Debugger** or click the ⚙ icon in the tool window toolbar.

| Setting | Default | Effect |
|---|---|---|
| Enable Spring Boot Debugger | On | Master switch |
| Show notification balloon | On | Fires a popup balloon in addition to the tool window card |
| Focus tool window on error | On | Brings the Spring Debugger panel to the foreground automatically |
| Minimum confidence level | MEDIUM | LOW confidence diagnoses are hidden by default |
| Maximum history entries | 30 | Per-session cap |

---

## Confidence Levels

| Level | Meaning |
|---|---|
| **HIGH** | The signal uniquely identifies the problem; diagnosis is almost certainly correct |
| **MEDIUM** | Good probability match; edge cases exist |
| **LOW** | Heuristic; worth checking but not definitive |

LOW confidence diagnoses are hidden by default. Adjust the **Minimum confidence level** setting if you want to see them.

---

## Architecture

```
IntelliJ Event                    Plugin
─────────────────                 ──────────────────────────────────────────
ProcessListener (run)  ──────►  RunConsoleTap
SMTRunnerEventsAdapter (test)►  TestConsoleTap      →  LogExtractor
CompileTask (build)   ──────►  BuildOutputTap           │
                                                         ▼
                                                   RawSignal
                                                    (causedByClass,
                                                     causedByMessage,
                                                     bannerDescription,
                                                     relevantLines,
                                                     rawExcerpt)
                                                         │
                                                         ▼
                                               RuleBasedClassifier
                                               (first-match-wins,
                                                phase-filtered,
                                                DONE rules only,
                                                43 YAML rules)
                                                         │
                                                         ▼
                                                  DiagnosisCard
                                                         │
                                               ┌─────────┴──────────────┐
                                               ▼                        ▼
                                    DiagnosisHistoryService      NotificationBalloon
                                    (project-scoped, thread-safe)
                                               │
                                               ▼
                                       SpringDebuggerPanel
                                       (tool window: card + history)
```

### Rule format

Rules live in `src/main/resources/rules/spring-boot-rules.yaml`. A rule requires:

```yaml
- id: "2.1"
  name: "NoSuchBeanDefinitionException"
  phases: [STARTUP, TEST]
  taps: [RUN_CONSOLE, TEST_CONSOLE]
  signals:
    causedByClass: "NoSuchBeanDefinitionException"
    messageContains: "required a bean of type"
  diagnosis: "Spring cannot find a bean of the required type…"
  fix: "Annotate the class with @Service, @Component, or @Repository…"
  confidence: HIGH
  fixture: "fixtures/2.1-no-such-bean.log"
  status: DONE
```

A rule is DONE only when a fixture log file exists and `ClassifierFixtureTest` passes it.

---

## Accuracy

Real-world testing against 15 logs sourced from GitHub Issues and developer blogs:

- **13 of 13** expected matches were correct (100%)
- **0 false positives** (after the v0.1.1–v0.1.3 bug fixes)
- **2 acknowledged gaps** documented in [ACCURACY-ANALYSIS-v0.1.0.md](ACCURACY-ANALYSIS-v0.1.0.md)

See the full analysis document for per-rule results, root cause analysis of the two bugs found during testing, and the planned v0.2.0 improvements.

---

## Contributing

1. Add the rule to `spring-boot-rules.yaml` with `status: TODO`.
2. Create a realistic fixture log in `src/main/resources/fixtures/`.
3. Change `status: DONE`.
4. Run `./gradlew test` — `ClassifierFixtureTest` must pass (confidence must be HIGH or MEDIUM, not LOW).

Issues: https://github.com/jehadbaeth/spring-debugger/issues

---

## Roadmap

- **v0.2.0** — expand real-world test corpus to 25+ logs; source logs for Jackson recursion and DataIntegrityViolation
- **M6 proper** — register the build-output tap via a non-deprecated `CompileTask` API instead of the current rawExcerpt fallback (the one remaining correctness debt)
- **M8 / rule 13.8** — PSI enrichment to verify structural claims; this also unblocks rule 13.8 (MapStruct null-mapping), which stays TODO until the null-value-strategy claim can be verified rather than guessed
- **Future** — Actuator enrichment (query /actuator/health); Ollama LLM fallback for unrecognised errors (setting already present, not yet wired)

---

## License

Apache 2.0 — see [LICENSE](LICENSE)
