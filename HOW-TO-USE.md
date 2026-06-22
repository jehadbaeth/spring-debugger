# Spring Boot Debugger — How to Use

**Version:** 0.1.0  
**Compatible IDEs:** IntelliJ IDEA Community and Ultimate 2023.3+

---

## What does it do?

Spring Boot Debugger watches your IDE's run, test, and build output streams. When a Spring Boot error is detected it surfaces a single card with two sentences:

1. **Diagnosis** — the exact problem, named concisely.
2. **Fix** — the one action that resolves it.

No reading cascading stack traces. No Googling the exception class name.

---

## Installation

### From a release ZIP (manual install)

1. Download `spring-debugger-<version>.zip` from the [Releases](https://github.com/jehadbaeth/spring-debugger/releases) page.
2. Open IntelliJ IDEA.
3. Go to **Settings → Plugins → ⚙ gear icon → Install Plugin from Disk…**
4. Select the downloaded ZIP file and click **OK**.
5. Click **Restart IDE** when prompted.

### Building from source

```
git clone https://github.com/jehadbaeth/spring-debugger.git
cd spring-debugger
./gradlew buildPlugin
```

The plugin ZIP is produced at `build/distributions/spring-debugger-<version>.zip`. Install it via the same manual install steps above.

---

## First steps after installation

After restarting, you will see a **Spring Debugger** panel at the bottom of the IDE (next to the Run and Problems tabs).

Open the panel. It shows:

```
● Monitoring  ·  41 rules
```

The green dot means the plugin is active and watching. No further setup is needed for offline mode.

---

## Running your Spring Boot application

Run your Spring Boot app the usual way (**Shift+F10** or the Run button). If the application fails to start:

- The tool window activates automatically.
- The **most recent diagnosis** card appears at the top.
- The card shows the rule ID, the phase (STARTUP / RUNTIME / TEST / COMPILE), the diagnosis sentence, and the fix sentence.
- Click **Copy Fix** to copy the fix sentence to the clipboard.
- Click **Copy Diagnosis** to copy the diagnosis sentence.

**Example card:**

```
[2.1]  STARTUP  HIGH

Diagnosis: Spring cannot find a bean of the required type because no
class with that type is registered in the application context.

Fix: Annotate the class with @Service, @Component, or @Repository,
or declare it as an @Bean; confirm it lives inside the package tree
rooted at your @SpringBootApplication class.
```

---

## Running Spring Boot tests

Run tests with **Shift+F10** or through the test runner. If the test application context fails to build the same diagnosis card appears in the tool window — including the relevant rule for missing beans, circular dependencies, or missing mocks.

---

## Reading the history list

Every diagnosis that crosses the minimum confidence threshold is added to the history list below the card area.

- **Double-click** any history entry to re-inspect that diagnosis card in the top panel.
- Click **Clear** in the history header to remove all entries from the current session.

History is stored in memory only and is not persisted across IDE restarts.

---

## Settings

Open **Settings → Tools → Spring Boot Debugger** (or click the **⚙** gear icon in the tool window toolbar).

| Setting | Default | Description |
|---|---|---|
| Enable Spring Boot Debugger | On | Master switch; turn off to pause monitoring without uninstalling. |
| Show notification balloon | On | Whether a balloon notification also fires in addition to the tool window card. |
| Focus tool window on error | On | Whether the Spring Debugger panel is automatically brought to the foreground when a diagnosis fires. |
| Minimum confidence level | MEDIUM | LOW confidence diagnoses are filtered out by default; raise to HIGH to see only the most certain matches. |
| Maximum history entries | 30 | How many past diagnoses to keep per session. |

### LLM Fallback (coming in a future release)

The settings panel includes an **Ollama LLM Fallback** section. This is intentionally disabled in 0.1.0 and will be wired up in a future release. When enabled it will route unrecognised errors through a local Ollama model — no data leaves your machine, which is why this approach was chosen over cloud LLM providers.

---

## Understanding rule IDs

Each rule has an ID like `2.1` or `13.4`. The number before the dot is the catalog section:

| Section | Topic |
|---|---|
| 1 | Application context and startup failures |
| 2 | Dependency injection errors |
| 3 | Configuration and properties errors |
| 4 | JPA / Hibernate / data access errors |
| 5 | Web, REST, and MVC errors |
| 6 | Spring Security errors |
| 7 | Jackson / serialization errors |
| 9 | Test context errors |
| 10 | Build, packaging, and classpath conflicts |
| 13 | MapStruct mapper errors |

---

## Confidence levels

| Level | Meaning |
|---|---|
| **HIGH** | The signal is specific enough that the diagnosis is almost certainly correct. |
| **MEDIUM** | The signal matches with good probability but there may be edge cases. |
| **LOW** | A heuristic match; worth checking but may not be the root cause. |

By default, LOW confidence diagnoses are not shown. Adjust **Minimum confidence level** in settings if you want to see them.

---

## What errors does it detect?

The plugin currently recognises 41 error patterns:

**Application context / startup (10 rules)**  
`ApplicationContextException`, `BeanDefinitionStoreException`, `BeanCreationException`, `BeanInstantiationException`, `BeanDefinitionOverrideException`, port already in use, no active profile, failed to load ApplicationContext (tests), missing auto-configuration, `BeanCurrentlyInCreationException`.

**Dependency injection (4 rules)**  
`NoSuchBeanDefinitionException`, `NoUniqueBeanDefinitionException`, `UnsatisfiedDependencyException`, circular dependency.

**Configuration and properties (3 rules)**  
Unresolved `@Value` placeholder, property type conversion failure, YAML syntax error.

**JPA / data access (6 rules)**  
No embedded database driver, failed to configure DataSource, HikariCP connection pool exhausted, entity not a managed JPA type, `LazyInitializationException`, `DataIntegrityViolationException`.

**Web / MVC (3 rules)**  
`NoHandlerFoundException` (404), `MethodArgumentNotValidException` (validation), ambiguous handler mapping.

**Spring Security (3 rules)**  
Default generated security password warning, `WebSecurityConfigurerAdapter` removed in Spring Security 6, no PasswordEncoder mapped.

**Jackson / serialization (2 rules)**  
Infinite recursion on bidirectional JPA relationship, cannot construct instance (no default constructor).

**Testing (2 rules)**  
Failed to load ApplicationContext in tests, `UnnecessaryStubbingException`.

**Build / packaging (3 rules)**  
`NoSuchMethodError` / version conflict, Lombok not generating code, Java version mismatch.

**MapStruct (5 rules)**  
Unmapped target properties, incompatible types in `@Mapping`, missing mapper implementation class, mapper not a Spring bean (missing `componentModel = "spring"`), no implementation type for return type, missing `@Mapper` annotation.

---

## Troubleshooting

**The tool window shows "Disabled"**  
Go to Settings → Tools → Spring Boot Debugger and enable the master switch.

**A diagnosis appeared but seems wrong**  
The confidence level shown on the card indicates certainty. LOW or MEDIUM confidence diagnoses can occasionally misfire. Check the rule ID and consult the catalog section in PLAN.md for the signals that triggered the match.

**No diagnosis appeared for an error I expected to be recognised**  
The error may not yet be covered, or the log format may differ from the fixture. Open an issue at https://github.com/jehadbaeth/spring-debugger/issues with the relevant log excerpt.

**The plugin is not detecting anything**  
Confirm the run configuration uses the standard IntelliJ run mechanism (not a terminal). The plugin attaches to IntelliJ's process listener API, which is not active for processes started outside the IDE.

---

## Reporting issues and contributing

Issues: https://github.com/jehadbaeth/spring-debugger/issues  
Pull requests welcome. When adding a new rule, the contract is:

1. Add the rule to `spring-boot-rules.yaml` with `status: TODO`.
2. Create a realistic fixture log file in `src/main/resources/fixtures/`.
3. Change `status: TODO` to `status: DONE`.
4. Run `./gradlew test` and confirm the `ClassifierFixtureTest` passes for your rule.

---

## License

Apache 2.0. See LICENSE file in the repository root.
