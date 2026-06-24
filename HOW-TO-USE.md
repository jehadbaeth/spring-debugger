# Spring Boot Debugger — How to Use

**Version:** 0.7.2  
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
● Monitoring  ·  57 rules
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

## Running from the Gradle or Maven tool window

When you launch a task from the Gradle or Maven panel (for example `bootRun` or `spring-boot:run`), or use delegated builds, the output streams through IntelliJ's external-system bus. Spring Boot Debugger taps that stream, so a compile failure, a startup failure, or a runtime/Kafka exception from a delegated run is diagnosed the same as a normal Run launch. No setup is needed.

---

## Monitoring a terminal

If you run your app or build from the integrated **Terminal** (for example `./gradlew bootRun` or `mvn spring-boot:run` typed directly), there is no process handle to attach to, so you tell the plugin which terminal to watch:

1. Open the **Spring Debugger** tool window.
2. Click the **▶ Monitor terminal** button in the panel toolbar.
3. Pick the terminal tab you want to watch. To stop, open the same menu and choose **Stop monitoring**.

While monitoring, the plugin polls that terminal's output and surfaces a diagnosis card when a Spring Boot error appears, exactly like the Run and Test taps. Only one terminal is watched at a time. Detection is poll-based (it reads the tab's scrollback, so you can attach after output has started) and may lag a second or two. The **Terminal** plugin must be enabled, and the **new (Gen2) Terminal** is not supported yet — if the chooser says no terminals are found, either turn the new Terminal off in **Settings > Tools > Terminal**, or simply run via the **Gradle/Maven tool window**, which is detected automatically with no attaching.

---

## Noisy integration runs (many errors at once)

When you run an integration suite (for example Robot Framework) against a running app, the app log can throw many server-side errors. The plugin handles this:

- It splits the log into individual error blocks and diagnoses each, so **every distinct** error surfaces, not just the last one.
- Repeats of the same error collapse into **one history row with a count** (e.g. a broken endpoint hit seven times shows `×7`), so the panel does not flood.
- Only the **first** error of a burst pops a balloon; the rest update the history quietly, so suites that intentionally trigger errors (negative tests) do not turn the plugin into noise.
- For a delegated `bootRun` from the Gradle/Maven panel, analysis now runs **while the app is still running**, not only when it stops.

Browse the grouped history to dig through what happened; double-click any row to bring its full diagnosis into the card.

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

### LLM Fallback (local Ollama)

The settings panel includes an **Ollama LLM Fallback** section. It is **off by default**. When enabled, any error that no rule recognises is routed through a local Ollama model, which returns a one-sentence diagnosis and one-sentence fix shown as an `llm` card at MEDIUM confidence.

| Setting | Default | Description |
|---|---|---|
| Enable Ollama LLM fallback | Off | Master switch for the fallback. When off, only the offline rule engine runs. |
| Ollama base URL | http://localhost:11434 | Where your local Ollama instance listens. |
| Ollama model | llama3.2 | The model name to query (must already be pulled in Ollama). |

How it behaves:

- The rule engine is always tried first. The LLM fires only when no rule matches.
- All data stays on your machine: requests go to your local Ollama instance, never to a cloud provider. This is deliberate, error logs can contain secrets.
- If the model reply cannot be parsed into both a diagnosis and a fix, nothing is shown rather than a malformed guess.
- The call is time-bounded so a slow or missing Ollama instance never hangs your run or test session.

To use it: install [Ollama](https://ollama.com), run `ollama pull llama3.2` (or your preferred model), then enable the fallback in settings.

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
| 14 | Spring Kafka errors |

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

The plugin currently recognises 57 error patterns:

**Application context / startup (10 rules)**  
`ApplicationContextException`, `BeanDefinitionStoreException`, `BeanCreationException`, `BeanInstantiationException`, `BeanDefinitionOverrideException`, port already in use, no active profile, failed to load ApplicationContext (tests), missing auto-configuration, `BeanCurrentlyInCreationException`.

**Dependency injection (4 rules)**  
`NoSuchBeanDefinitionException`, `NoUniqueBeanDefinitionException`, `UnsatisfiedDependencyException`, circular dependency.

**Configuration and properties (3 rules)**  
Unresolved `@Value` placeholder, property type conversion failure, YAML syntax error.

**JPA / data access (7 rules)**  
No embedded database driver, failed to configure DataSource, HikariCP connection pool exhausted, entity not a managed JPA type, `LazyInitializationException`, `DataIntegrityViolationException`, missing `RedisConnectionFactory`.

**Web / MVC (3 rules)**  
`NoHandlerFoundException` (404), `MethodArgumentNotValidException` (validation), ambiguous handler mapping.

**Spring Security (3 rules)**  
Default generated security password warning, `WebSecurityConfigurerAdapter` removed in Spring Security 6, no PasswordEncoder mapped.

**Jackson / serialization (2 rules)**  
Infinite recursion on bidirectional JPA relationship, cannot construct instance (no default constructor).

**Testing (2 rules)**  
`UnnecessaryStubbingException`, Testcontainers Docker environment not available. (A generic "failed to load ApplicationContext" catch-all also fires for any other unrecognised test context failure.)

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
