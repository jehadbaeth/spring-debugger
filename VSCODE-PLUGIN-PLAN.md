# Spring Boot Debugger: VS Code Extension Implementation Plan

**Target editor:** Visual Studio Code (and VS Code based editors: Cursor, Windsurf, VSCodium)
**Extension language:** TypeScript (extension host) + a reused headless Java core (rule engine)
**Rule catalog:** the existing `spring-boot-rules.yaml` (shared, single source of truth)
**Status:** Living document. Implementation details below are the current intended approach and are expected to change as the work proceeds. Decisions are recorded in section 15; revisit them rather than rewriting history.

---

## 1. Goal and Scope

Bring the IntelliJ plugin's behaviour to VS Code with the same feature set: watch the output a Spring Boot project produces during run, test, and build, and when an error is detected surface a short card with one sentence naming the problem and one sentence giving the fix. No stack-trace reading required.

The user-visible promise is identical to the IntelliJ plugin. The mechanisms differ because the two host platforms expose completely different APIs, and one VS Code limitation (section 4) shapes the whole capture design.

### In scope (feature parity targets)

Every capability the IntelliJ plugin shipped through v0.10.0:

1. Rule based diagnosis over Spring Boot startup, runtime, test, and build output (60 rules at time of writing).
2. Multi error extraction from a noisy log, deduplicated, with occurrence counts.
3. Run boundary awareness for appending log files (only diagnose the latest run; re-runs re-surface).
4. Capture paths that do not depend on reading the terminal:
   - Test results watching (`build/test-results`, `surefire-reports`).
   - Log file tailing with auto discovery of `logging.file.name` across all modules.
   - Diagnose pasted output command.
5. Diagnosis history with re-inspection.
6. Copy fix / copy diagnosis.
7. Settings for confidence threshold, history size, notifications, and the capture toggles.
8. Offline by design; optional local Ollama fallback for unrecognised errors.

### Deferred or reduced parity (called out honestly)

- **PSI / source aware enrichment.** The IntelliJ plugin inspects parsed Java source to confirm structural claims ("the missing bean has no stereotype", "this type is a `@Mapper`", "the class is outside the component scan tree") and upgrade confidence. VS Code has no built-in Java model. This is the single largest parity gap. See section 7. First releases ship without it; rules still fire from text signals.
- **Internal build tap.** IntelliJ taps its own JPS compiler. VS Code has no equivalent internal build; the build path is covered through tasks output and the log/test files instead.
- **Reading the integrated terminal.** Not possible on either platform's new terminal; not attempted. See section 4.

### Out of scope (same as IntelliJ plugin)

- Generic Java compiler errors, non Spring frameworks, non Java languages.

---

## 2. Why a Shared Java Core (architecture decision)

Two strategies were considered. The recommended one is a shared headless core.

### Option A (recommended): shared headless Java core + thin TypeScript shell

Extract the platform independent engine from the IntelliJ plugin into a standalone module with no IntelliJ imports, exposing a single entry point roughly of the form `diagnose(text, context) -> DiagnosisCard[]`. Ship it as a small executable (CLI over stdin/stdout, or a Language Server style process). The VS Code extension is TypeScript that captures output, sends text to the core, and renders the returned cards.

Rationale:
- The 60 rules and the classifier are the actual asset. Maintaining two diverging copies (Java and a TypeScript reimplementation) would be a constant tax; this session alone changed the rules and the segmenter several times.
- The existing 203 tests stay meaningful and keep guarding both products.
- Spring developers already have a JVM available, so the runtime dependency is acceptable.

Cost:
- A decoupling refactor of the current plugin to separate an `engine-core` module from the IntelliJ `platform` module. Some classes already leak IntelliJ types (for example `ConsoleDiagnoser` takes a `Project`, though it tolerates null; enrichers use PSI). These must be split behind interfaces.
- A process boundary (start the core, speak a small protocol, manage its lifecycle) and JAR bundling in the extension.

### Option B: full TypeScript rewrite

Reimplement the engine and rules in TypeScript; no JVM at runtime.

Rejected as the primary path because of dual maintenance and rule drift, which this codebase has shown to be a real and frequent cost. It remains a fallback if the JVM dependency proves unacceptable to the team (see section 14, open questions).

### Module shape (target)

```
spring-debugger/
  engine-core/        (new) pure Java: rules, classifier, extractor, segmenter,
                      parsers, run-boundary, models. No IntelliJ imports. The 203 tests move here.
  platform-intellij/  the current plugin, depends on engine-core.
  engine-cli/         (new) thin main() wrapping engine-core: reads text, writes JSON cards.
  vscode-extension/   (new) TypeScript extension; bundles engine-cli JAR; captures + renders.
```

The first concrete task (independent of VS Code) is extracting `engine-core`. It improves the IntelliJ plugin's testability regardless of whether the VS Code port proceeds.

---

## 3. Feature Parity Matrix

| Capability | IntelliJ mechanism | VS Code mechanism | Risk |
|---|---|---|---|
| Rule diagnosis | `RuleBasedClassifier` over taps | same engine via core process | Low |
| Run launch capture | `ExecutionManager.EXECUTION_TOPIC` process listener | Debug adapter tracker (section 5.4); else log file | Medium |
| Test capture | SMT test runner + `TestResultsWatchService` | file watcher on result XML (section 5.1) | Low |
| Gradle/Maven panel | external system task bus | not applicable; use task output + log/test files | Medium |
| Terminal bootRun | classic terminal poll / log tail | log file tailing only (section 5.2) | Medium |
| Diagnose pasted output | toolbar action + input dialog | command + input box / paste from clipboard | Low |
| Multi error + dedup | `ConsoleDiagnoser.diagnoseAll` | same (in core) | Low |
| Run boundary handling | `LogRunBoundary` | same (in core) | Low |
| Diagnosis card UI | tool window panel | webview panel or rich tree item | Medium |
| History | tool window list + service | TreeView backed by extension state | Low |
| Settings | `Configurable` + persisted state | `package.json` `contributes.configuration` | Low |
| PSI enrichment | IntelliJ PSI | jdt.ls heuristics or omit | High |
| Actuator enrichment | HTTP to `/actuator/health` | Node HTTP, identical logic | Low |
| LLM fallback | Ollama HTTP | Node HTTP, identical logic | Low |

---

## 4. The Defining Constraint: Terminal Output Is Not Readable

VS Code extensions cannot read the text written to an integrated terminal. The relevant API (`window.onDidWriteTerminalData`) has been proposed and unstable for years and cannot be relied on in a published extension. This is the same wall the IntelliJ plugin hit with the new Gen2 terminal.

The consequence is the same conclusion the IntelliJ work reached: **capture the output, not the terminal.** Everything in section 5 follows from this. The good news is that the IntelliJ plugin already pivoted to this model (test result files, log file tailing, paste command), so the design is proven and ports directly. A developer typing `./gradlew bootRun` or `./gradlew test` in the VS Code terminal is served by file based capture, not by reading the terminal.

Shell integration (the VS Code feature that knows command start/end and exit codes) can tell us *that* a command ran and whether it failed, but not its output text. It is useful only as a trigger hint, not as a content source.

---

## 5. Capture Layer Design

All capture paths converge on the same call: hand a block of text to the engine core, receive cards, render them. The capture paths differ only in where the text comes from and when.

### 5.1 Test results watcher (zero config, primary win)

Port of `TestResultsWatchService` + `TestResultsParser` + `TestResultsLocator`.

- Use `vscode.workspace.createFileSystemWatcher` with a glob such as `**/build/test-results/test/*.xml` and `**/target/surefire-reports/*.xml` and `**/target/failsafe-reports/*.xml`.
- Caveat to verify: VS Code respects `files.watcherExclude`, which often excludes `**/build/**`. The extension may need to register an explicit watcher outside the exclude, or fall back to polling (as the IntelliJ version does for the same reason, because `build/` is excluded there too). Decision pending verification (section 15).
- On change, read the XML, extract `<failure>`/`<error>` blocks (the parser logic is in core), feed each to the engine, dedupe per batch, render. Baseline on activation so pre-existing results are not replayed.

### 5.2 Log file tailing (bootRun, near zero config)

Port of `LogFileTailService` + `LogFilePropertyFinder` + `LogRunBoundary`.

- Discover every `logging.file.name` across all `application*.properties` / `application*.yml` (logic already in core). Resolve relative paths against each module.
- Tail each file by byte offset using Node `fs`. The run boundary logic (only diagnose the latest run, reset dedup on a new `Starting ... with PID` line) is reused from core unchanged.
- Same honesty as IntelliJ: console only apps need a committed `logging.file.name`. Document it identically.
- `fs.watch` is event based but unreliable across platforms for some editors; a polling fallback (the IntelliJ approach) is the safe default.

### 5.3 Diagnose pasted output (always works)

Port of the paste command. A command `springDebugger.diagnosePasted` opens an input box (or reads the clipboard via `vscode.env.clipboard.readText()`), sends the text to the engine, renders all cards. Zero platform risk; this is the guaranteed fallback for any capture gap, exactly as in the IntelliJ plugin.

### 5.4 Run / Debug capture (best effort)

When the user launches Spring Boot through VS Code's Run and Debug (the Java extension's debug configuration), an extension can observe the debug session via `vscode.debug.registerDebugAdapterTrackerFactory` and read output events. This gives a real capture path for the Run button equivalent, similar in spirit to the IntelliJ `RunConsoleTap`. Treat as best effort: output event coverage varies by debug adapter.

### 5.5 Tasks output (build and test via tasks.json)

If the team runs Gradle/Maven through VS Code tasks, `vscode.tasks.onDidEndTaskProcess` gives exit codes and the task can carry a problem matcher. This is a trigger and a structured error channel, not full text. Lower priority; the file based paths (5.1, 5.2) cover the same runs more completely.

### Capture priority for the MVP

1. Test results watcher (5.1).
2. Log file tailing (5.2).
3. Diagnose pasted output (5.3).
4. Debug adapter tracker (5.4) as a later enhancement.

---

## 6. Rule Engine Reuse

- `engine-core` exposes `diagnose(text, context)` returning a list of cards as plain data (id, phase, diagnosis, fix, confidence, excerpt, groupingKey).
- `engine-cli` wraps it: read a request on stdin (text plus minimal context such as project base path for discovery, and optional actuator/LLM config), write a JSON array of cards on stdout. A long lived process speaking newline delimited JSON avoids per call JVM startup cost. An LSP framing is an alternative if we want richer lifecycle.
- The VS Code extension spawns this process once per workspace, keeps it warm, and sends each captured block to it.
- Rule catalog stays a single `spring-boot-rules.yaml` packaged inside the core. No rule logic in TypeScript.

---

## 7. Enrichment

### 7.1 PSI / source aware enrichment (the hard gap)

The IntelliJ plugin uses PSI to verify structural claims and raise confidence. VS Code has no built-in Java model. Options, in increasing cost:

1. **Omit at first.** Rules still fire from text signals at their declared confidence. Acceptable for MVP; this is the planned starting point.
2. **Lightweight heuristics.** Regex/AST-lite scans of the workspace source for annotations and package layout to approximate the most valuable checks (stereotype presence, component scan tree). Cheap, imperfect.
3. **Java language server integration.** Drive `redhat.java` (jdt.ls) through available commands or an LSP request to inspect symbols. Highest fidelity, highest cost and coupling, and the jdt.ls extension API for third parties is limited. Evaluate only after MVP.

The enrichment interface in `engine-core` should be a pluggable port so the IntelliJ PSI enricher and a future VS Code enricher are two implementations of the same contract. This keeps the core honest and the gap contained.

### 7.2 Actuator enrichment

Pure HTTP to `/actuator/health`. Reimplement in the extension or keep in the core CLI (preferred, so it stays one implementation). Low risk.

### 7.3 Property precedence enrichment

Logic is data driven and lives in core; reused directly.

---

## 8. UI and UX

VS Code idioms differ from a tool window. Proposed surfaces:

- **Activity bar view container** "Spring Debugger" with:
  - a **History tree** (TreeView) of diagnoses, newest first, with occurrence counts; selecting an item opens its card.
  - a status row showing active capture paths and rule count.
- **Diagnosis card**: a Webview panel rendering the two sentences, phase, confidence, rule id, and Copy buttons. Alternatively a Markdown hover/notification for a lighter footprint; the Webview gives the closest parity to the IntelliJ card.
- **Notifications**: `window.showWarningMessage` for the first error of a burst (mirrors the balloon and the "only first of burst" rule), with a "Show" action that reveals the card. Quiet updates for the rest.
- **Status bar item**: compact indicator (watching / last diagnosis), optional.
- **Commands** (command palette): Diagnose pasted output, Diagnose current file selection, Start/stop log watching, Clear history, Open settings.

History and burst dedup semantics (counts, first-of-burst balloon) come from the same model as IntelliJ so behaviour matches.

---

## 9. Settings Mapping

VS Code settings via `contributes.configuration` in `package.json`, namespace `springDebugger.*`.

| IntelliJ setting | VS Code setting | Default |
|---|---|---|
| Enable | `springDebugger.enabled` | true |
| Minimum confidence | `springDebugger.minimumConfidence` | MEDIUM |
| Max history entries | `springDebugger.maxHistory` | 30 |
| Show notification balloon | `springDebugger.showNotifications` | true |
| Focus tool window on error | `springDebugger.revealOnError` | true |
| Watch test results | `springDebugger.watchTestResults` | true |
| Watch log file | `springDebugger.watchLogFile` | true |
| Log file path | `springDebugger.logFilePath` | "" (auto discover) |
| LLM fallback enabled | `springDebugger.ollama.enabled` | false |
| Ollama base URL | `springDebugger.ollama.baseUrl` | http://localhost:11434 |
| Ollama model | `springDebugger.ollama.model` | llama3.2 |

(The experimental new terminal toggle has no VS Code analogue and is dropped.)

---

## 10. Packaging and Distribution

- Bundle the `engine-cli` JAR inside the `.vsix`. Size is acceptable (a few MB).
- **JVM discovery**: locate a JRE via, in order, `springDebugger.javaHome` setting, `JAVA_HOME`, the `redhat.java` extension's embedded JRE if present, then `PATH`. If none is found, disable the engine gracefully and tell the user once, with the paste command and settings link. Never crash the host.
- **Degraded no-JVM mode** (optional, later): if the team rejects the JVM dependency, this is the trigger to reconsider Option B for a pure-TS core. Documented as a fork point, not built now.
- Publish to the VS Code Marketplace and Open VSX (for Cursor / VSCodium). Use `vsce` and `ovsx`.
- Versioning independent from the IntelliJ plugin, but the bundled core version is recorded in release notes so rule parity is traceable.

---

## 11. Testing Strategy

- **Engine**: the existing 203 Java tests move to `engine-core` and keep running in CI. They are the parity guarantee.
- **CLI protocol**: a small Java test that feeds known logs through `engine-cli` and asserts the JSON cards (the same real-world fixtures used today, including the multi-module bootRun log and the schema-out-of-sync fixtures).
- **Extension logic**: unit test the TypeScript capture glue (file discovery globs, offset tailing, debounce, dedup wiring) with the extension test runner.
- **Integration**: `@vscode/test-electron` smoke tests that open a fixture workspace, drop a result XML / append to a log file, and assert a diagnosis is produced and shown.
- **Manual validation**: the same loop that worked this session. Real terminal `./gradlew test` and `bootRun` with infra down, confirm cards. The team has been an effective validation partner; keep that loop.

---

## 12. Privacy and Security Parity

Same posture as the IntelliJ plugin: fully offline by default, no network egress. The only outbound calls are the optional local Ollama fallback and the optional local actuator probe, both off or local. State (history, settings) stays in workspace/global storage. State this explicitly in the marketplace listing.

---

## 13. Phased Roadmap

Each milestone is shippable and has an exit test.

**M0. Core extraction (no VS Code yet).**
Split `engine-core` out of the IntelliJ plugin with zero IntelliJ imports; move the tests; keep the IntelliJ plugin green on top of it. Exit: IntelliJ plugin builds and all tests pass against the extracted core.

**M1. Engine CLI.**
`engine-cli` long-lived process, newline-delimited JSON in/out, packaged JAR. Exit: piping the real-world fixtures through the CLI yields the expected card sets (matches the Java tests).

**M2. VS Code skeleton + paste command.**
Extension activates, spawns the core, implements Diagnose pasted output end to end with the Webview card. Exit: pasting the multi-module bootRun log shows the three expected cards.

**M3. Test results watching.**
File watcher (or polling fallback) over result XML, dedup, history TreeView. Exit: `./gradlew test` with a failing context test in the VS Code terminal produces a card with no configuration.

**M4. Log file tailing.**
Auto discovery across modules, multi-file tailing, run boundary handling, settings. Exit: terminal `bootRun` with `logging.file.name` committed and infra down produces DB and Kafka cards, re-runs re-surface, stale runs ignored.

**M5. Notifications, status, settings polish, JVM discovery, packaging.**
Exit: installable `.vsix` that degrades cleanly with no JVM and publishes to Marketplace and Open VSX.

**M6. Enrichment and Run/Debug capture (parity stretch).**
Actuator and LLM via the core; debug adapter tracker capture (5.4); evaluate jdt.ls based PSI-equivalent enrichment. Exit: confidence upgrades demonstrably improve on at least the highest value checks, or a documented decision to stay text-only.

---

## 14. Effort Estimate

Assuming one developer fluent in TypeScript and Java, accepting the JVM dependency, and shipping MVP without PSI enrichment:

- M0 core extraction: about 1 week (also benefits the IntelliJ plugin).
- M1 CLI: a few days.
- M2 to M4 (the capture core and UI): about 2 to 3 weeks.
- M5 packaging and robustness: about 1 week.
- M6 enrichment and debug capture: open ended; weeks if jdt.ls integration is pursued, near zero if deferred.

MVP through M5 is roughly **3 to 4 weeks**. Full parity including source aware enrichment is the long pole and is deliberately deferred.

---

## 15. Open Questions and Decisions to Revisit

1. **JVM dependency acceptable?** The whole shared-core strategy rests on this. If no, switch to a TypeScript engine (Option B) and accept dual maintenance. Needs a team decision.
2. **File watcher vs polling for `build/` outputs.** VS Code `files.watcherExclude` likely hides `build/`. Verify whether an explicit watcher fires; default to polling if not (matches IntelliJ).
3. **Card surface: Webview vs Tree+hover.** Webview is closest to parity but heavier; decide after a UX spike in M2.
4. **CLI vs LSP framing for the core process.** Start with newline-delimited JSON; promote to LSP only if lifecycle needs grow.
5. **How much PSI parity is worth rebuilding.** Quantify which enrichment checks actually change outcomes on real logs before investing in jdt.ls.
6. **Editor targets.** Marketplace only, or Open VSX too (Cursor/Windsurf/VSCodium). Leaning both.

---

## 16. Decisions Log

- 2026-06-25: Plan created. Chose shared headless Java core (Option A) over TypeScript rewrite (Option B), pending confirmation of the JVM dependency (open question 1). MVP scope set to M0 through M5, PSI enrichment deferred to M6. Terminal reading explicitly out of scope on the same grounds as the IntelliJ Gen2 terminal.
