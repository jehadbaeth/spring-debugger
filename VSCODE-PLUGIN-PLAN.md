# Spring Boot Debugger: VS Code Extension Implementation Plan

**Target editor:** Visual Studio Code (and VS Code based editors: Cursor, Windsurf, VSCodium)
**Extension language:** TypeScript, with the diagnosis engine ported to TypeScript (no JVM at runtime)
**Rule catalog:** the existing `spring-boot-rules.yaml`, kept as the single shared source of truth for both products
**Repository:** this repository. The VS Code extension is built here alongside the IntelliJ plugin and ships as an additional release artifact. No separate repo.
**Status:** Living document. Implementation details below are the current intended approach and are expected to change as the work proceeds. Decisions are recorded in section 16; revisit them rather than rewriting history.

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
8. Offline by design.

What "parity" means here, stated precisely: the implemented work is **engine parity** over the rule
catalog, proven card-for-card against the Java golden across 93 corpus logs. It is **not** full
feature parity with the shipping IntelliJ plugin, because the enrichment layer and the LLM fallback
are not yet ported (see below and M5). On the same input, VS Code may therefore show a diagnosis at a
lower confidence or with less project-specific detail than IntelliJ.

### Deferred or reduced parity (called out honestly)

- **Enrichment layer (PSI, Actuator, PropertyPrecedence) — deferred to M5.** The IntelliJ plugin runs
  three enrichers at runtime: PSI inspects parsed Java source to confirm structural claims ("the
  missing bean has no stereotype", "this type is a `@Mapper`", "the class is outside the component
  scan tree") and upgrade confidence; Actuator confirms a live app's health; PropertyPrecedence
  sharpens config messages. The TypeScript `ConsoleDiagnoser` is **rule-engine-only** today. PSI has
  no VS Code equivalent and is the single largest gap (section 7); Actuator and PropertyPrecedence are
  straightforward Node ports but are not done yet. Until then, rules fire from text signals at their
  declared base confidence.
- **LLM (Ollama) fallback — deferred to M5.** Not ported. The implemented settings deliberately omit
  the `ollama.*` keys so the UI does not promise a feature that is absent.
- **Confidence note.** Because background capture filters at `minimumConfidence` (default MEDIUM) on
  *base* confidence, a diagnosis IntelliJ would surface only after an enrichment upgrade would stay
  hidden in VS Code. In practice the current corpus has zero LOW-confidence cards (90 HIGH, 8
  MEDIUM), so the default threshold hides nothing today; this matters only once LOW rules or
  enrichment-dependent confidence enter the picture.
- **Internal build tap.** IntelliJ taps its own JPS compiler. VS Code has no equivalent internal build; the build path is covered through tasks output and the log/test files instead.
- **Reading the integrated terminal.** Not possible on either platform's new terminal; not attempted. See section 4.

### Out of scope (same as IntelliJ plugin)

- Generic Java compiler errors, non Spring frameworks, non Java languages.

---

## 2. Architecture: TypeScript Engine, Shared Rules and Fixtures (decision)

The chosen path is a native TypeScript engine inside the extension. No JVM, no bundled JAR, no out-of-process core. This was decided on 2026-06-25 (section 16): for VS Code, a pure TypeScript extension is the simpler and more native integration, the install is lighter, and there is no process lifecycle to manage. The team is comfortable porting the logic and the rules.

### The honest cost, and how this plan contains it

A TypeScript rewrite means the classifier logic exists in two languages. Logic drift between the Java and TypeScript engines is a real and recurring cost; this codebase changed the rules and the segmenter several times in a single session. The plan does not wave this away. It contains drift with three concrete disciplines:

1. **The rule catalog stays a single shared data file.** `spring-boot-rules.yaml` is data, not code. Both products read the exact same file from this repo. The TypeScript engine parses the identical YAML; it does not get its own hand-edited copy. At package time the file is copied into the `.vsix`; it is never forked. A rule added for one product is a rule added for both, automatically.
2. **The test fixtures stay shared.** The real-world logs and the synthetic fixtures (including the multi-module bootRun log and the schema-out-of-sync fixtures) live once in the repo and are consumed by both test suites.
3. **A parity test gates CI.** A cross-engine parity suite feeds every shared fixture through both the Java engine and the TypeScript engine and asserts the same cards come out (same rule ids, same phase, same dedup/occurrence behaviour). If the two engines disagree, CI fails. This turns "hope they stay in sync" into "the build breaks when they drift." This suite is the load-bearing safeguard of the whole TypeScript decision and is built in M0, before any feature work.

What still has to be ported by hand, and kept in parity by the suite above: the classifier (first-match-wins, phase filtering, DONE-only, `SignalCriteria` AND/`messageContainsAny` OR semantics, deepest-cause matching for `causedByClass`/`causedByMessage`), the extractor, the `StackTraceSegmenter` boundaries, the `TestResultsParser` (XXE-hardened XML parse), the `LogFilePropertyFinder` discovery, the `LogRunBoundary` slicing, and the `ConsoleDiagnoser.diagnoseAll` segmentation + dedup. These are pure functions with no platform coupling, which is what makes a faithful port tractable.

### What was rejected

A shared headless Java core invoked out-of-process (CLI or LSP) from the extension was considered and rejected for VS Code. It would have kept one engine, but at the price of a bundled JVM, JVM discovery, JAR packaging, and a process boundary to manage inside the extension host. For a VS Code audience that is a heavier and less native install than the port is worth. The IntelliJ plugin remains the Java engine; the VS Code extension is its TypeScript sibling, kept honest by shared data and the parity suite.

### Repository shape (target)

Single repo, two products, shared data.

```
spring-debugger/
  src/                         the IntelliJ plugin (Java engine + IntelliJ platform glue). Unchanged.
  src/main/resources/rules/
    spring-boot-rules.yaml      THE single rule catalog. Shared, not forked.
  fixtures/ (+ existing test fixtures)
                               shared real-world logs and synthetic fixtures, consumed by both suites.
  vscode-extension/            (new) the TypeScript extension and the ported TS engine.
    src/engine/                ported classifier, extractor, segmenter, parsers, run-boundary, models.
    src/capture/               file watchers, log tailing, paste, debug tracker.
    src/ui/                    webview card, history tree, notifications.
    test/                      TS unit tests + the TS side of the parity suite.
  parity/                      (new) cross-engine parity harness and shared fixture manifest.
```

The build copies `spring-boot-rules.yaml` and the shared fixtures into `vscode-extension/` at build/package time (a copy step, not a committed duplicate) so there is exactly one editable source.

---

## 3. Feature Parity Matrix

| Capability | IntelliJ mechanism | VS Code mechanism | Risk |
|---|---|---|---|
| Rule diagnosis | `RuleBasedClassifier` over taps | ported TypeScript engine, same shared rules | Low |
| Run launch capture | `ExecutionManager.EXECUTION_TOPIC` process listener | Debug adapter tracker (section 5.4); else log file | Medium |
| Test capture | SMT test runner + `TestResultsWatchService` | file watcher on result XML (section 5.1) | Low |
| Gradle/Maven panel | external system task bus | not applicable; use task output + log/test files | Medium |
| Terminal bootRun | classic terminal poll / log tail | log file tailing only (section 5.2) | Medium |
| Diagnose pasted output | toolbar action + input dialog | command + input box / paste from clipboard | Low |
| Multi error + dedup | `ConsoleDiagnoser.diagnoseAll` | ported equivalent in TS engine | Low |
| Run boundary handling | `LogRunBoundary` | ported equivalent in TS engine | Low |
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

All capture paths converge on the same call: hand a block of text to the TypeScript engine, receive cards, render them. The capture paths differ only in where the text comes from and when.

### 5.1 Test results watcher (zero config, primary win)

Port of `TestResultsWatchService` + `TestResultsParser` + `TestResultsLocator`.

- Use `vscode.workspace.createFileSystemWatcher` with globs such as `**/build/test-results/test/*.xml`, `**/target/surefire-reports/*.xml`, `**/target/failsafe-reports/*.xml`.
- Caveat to verify: VS Code respects `files.watcherExclude`, which often excludes `**/build/**`. The extension may need to register an explicit watcher outside the exclude, or fall back to polling (as the IntelliJ version does for the same reason, because `build/` is excluded there too). Decision pending verification (section 16).
- On change, read the XML, extract `<failure>`/`<error>` blocks (ported parser, XXE-hardened), feed each to the engine, dedupe per batch, render. Baseline on activation so pre-existing results are not replayed.

### 5.2 Log file tailing (bootRun, near zero config)

Port of `LogFileTailService` + `LogFilePropertyFinder` + `LogRunBoundary`.

- Discover every `logging.file.name` across all `application*.properties` / `application*.yml`. Resolve relative paths against each module (the `resolveAgainstModule` logic, stripping `/src/`).
- Tail each file by byte offset using Node `fs`. The run boundary logic (only diagnose the latest run, reset dedup on a new `Starting ... with PID` line, reset offset on truncation) is ported unchanged.
- Same honesty as IntelliJ: console only apps need a committed `logging.file.name`. Document it identically, including that Spring Boot 3.x uses `logging.file.name` not the removed `logging.file`.
- `fs.watch` is event based but unreliable across platforms for some editors; a polling fallback (the IntelliJ approach, ~poll every few seconds, re-discover periodically) is the safe default.

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

## 6. Engine Port (TypeScript)

- A single entry point, roughly `diagnose(text, context): DiagnosisCard[]`, mirroring the Java `ConsoleDiagnoser.diagnoseAll` contract: segment via the ported `StackTraceSegmenter`, classify each block first-match-per-block, dedupe by `groupingKey` (`ruleId + "|" + diagnosisSentence`).
- The YAML is parsed at activation with a small dependency (for example `js-yaml`), producing the same rule model the Java side builds. Rule semantics ported faithfully: phase filtering, DONE-only, `messageContains` (single) vs `messageContainsAny` (OR), `causedByClass`/`causedByMessage` matching the deepest cause, AND across non-null criteria.
- Card model is plain data (id, phase, diagnosis, fix, confidence, excerpt, groupingKey), identical fields to the Java card so the parity suite can compare directly.
- Enrichment is behind a port/interface so actuator, property-precedence, and a future jdt.ls source enricher are pluggable (section 7).

---

## 7. Enrichment

### 7.1 PSI / source aware enrichment (the hard gap)

The IntelliJ plugin uses PSI to verify structural claims and raise confidence. VS Code has no built-in Java model. Options, in increasing cost:

1. **Omit at first.** Rules still fire from text signals at their declared confidence. Acceptable for MVP; this is the planned starting point.
2. **Lightweight heuristics.** Regex/AST-lite scans of the workspace source for annotations and package layout to approximate the most valuable checks (stereotype presence, component scan tree). Cheap, imperfect.
3. **Java language server integration.** Drive `redhat.java` (jdt.ls) through available commands or an LSP request to inspect symbols. Highest fidelity, highest cost and coupling, and the jdt.ls extension API for third parties is limited. Evaluate only after MVP.

Keep enrichment behind a pluggable port in the TypeScript engine so the omit/heuristic/jdt.ls variants are swappable without touching the classifier.

### 7.2 Actuator enrichment

Pure HTTP to `/actuator/health`, reimplemented with Node HTTP. Identical logic to the Java enricher. Low risk.

### 7.3 Property precedence enrichment

Data driven; port the logic directly.

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
| LLM fallback enabled | `springDebugger.ollama.enabled` | _M5, not implemented yet_ |
| Ollama base URL | `springDebugger.ollama.baseUrl` | _M5, not implemented yet_ |
| Ollama model | `springDebugger.ollama.model` | _M5, not implemented yet_ |

The rows above the LLM block are implemented. The `ollama.*` keys are intentionally absent from the
current `contributes.configuration` until the LLM fallback is ported in M5, so the settings UI never
offers a control that does nothing. The experimental new terminal toggle has no VS Code analogue and
is dropped.

---

## 10. Packaging, Distribution, and Release Process

The VS Code extension is an additional artifact of this repo's existing release process, not a separate release.

- **Build artifact**: a `.vsix` produced from `vscode-extension/`. Pure TypeScript bundle (esbuild/webpack), no JVM, no bundled JAR. The shared `spring-boot-rules.yaml` is copied in at package time.
- **Release coupling**: each tagged release of this repo attaches both the IntelliJ plugin `.zip` and the VS Code `.vsix`. Release notes state which `spring-boot-rules.yaml` revision both were built from, so rule parity is traceable from a single tag. Versioning can stay unified across both artifacts since they ship together from one repo; if they ever need to diverge, note it explicitly in the release.
- **Marketplaces**: publish the `.vsix` to the VS Code Marketplace (`vsce`) and Open VSX (`ovsx`, for Cursor / Windsurf / VSCodium). This is independent of the JetBrains Marketplace publish for the IntelliJ plugin.
- **No JVM dependency** to discover or degrade around. One fewer failure mode than the rejected shared-core approach.

---

## 11. Testing Strategy

- **Cross-engine parity suite (the safeguard).** Built first, in M0. Feeds every shared fixture through both the Java engine and the TypeScript engine and asserts identical cards. Fails CI on any divergence. This is what makes the two-language engine safe.
- **TypeScript engine unit tests.** Port the meaningful Java unit tests (segmenter boundaries, classifier semantics, parser, run-boundary, property finder) to the TS suite so the TS engine is independently covered, not only checked by parity.
- **Java engine tests.** The existing 203 tests stay and keep guarding the Java engine.
- **Extension logic tests.** Unit test the TypeScript capture glue (file discovery globs, offset tailing, debounce, dedup wiring).
- **Integration.** `@vscode/test-electron` smoke tests that open a fixture workspace, drop a result XML / append to a log file, and assert a diagnosis is produced and shown.
- **Manual validation.** The same loop that worked this session: real terminal `./gradlew test` and `bootRun` with infra down, confirm cards. The team has been an effective validation partner; keep that loop.

---

## 12. Privacy and Security Parity

Same posture as the IntelliJ plugin: fully offline by default, no network egress. The only outbound calls are the optional local Ollama fallback and the optional local actuator probe, both off or local. State (history, settings) stays in workspace/global storage. State this explicitly in the marketplace listing.

---

## 13. Phased Roadmap

Status as of 2026-06-25: **M0 through M4 are implemented and green** (139 TypeScript tests, the
Java golden asserted in CI, the `.vsix` packages). M5 (source-aware enrichment and Run/Debug
capture) is the deferred stretch. The extension lives in `vscode-extension/`.

Each milestone is shippable and has an exit test.

**M0. TypeScript engine port + parity harness (no UI yet).** ✅ Done.
Scaffold `vscode-extension/`, port the engine (classifier, extractor, segmenter, parser, run-boundary, property finder, models) to TypeScript reading the shared YAML, and stand up the cross-engine parity suite over the shared fixtures. Exit: parity suite green, that is the TS engine produces the same cards as the Java engine for every shared fixture, and the TS unit tests pass.

**M1. VS Code skeleton + paste command.** ✅ Done.
Extension activates, loads the engine, implements Diagnose pasted output end to end with the Webview card. Exit met: the multi-module bootRun log yields the three expected cards (4.15 DB, 2.1 CloseApproach bean, 14.1 Kafka), verified through the engine.

**M2. Test results watching.** ✅ Done.
Polling watcher (the safe default, since build/ is often watcher-excluded) over result XML, per-batch dedup, history TreeView in an activity-bar view. Exit met by the watcher unit tests (baseline, surface-after-baseline, no-replay, re-run, batch dedup).

**M3. Log file tailing.** ✅ Done.
Auto discovery across modules, multi-file tailing by byte offset, run-boundary slicing, truncation reset, per-run dedup, logFilePath override. Exit met by the tailer unit tests including stale-run suppression and re-run re-surfacing.

**M4. Notifications, status, settings polish, packaging.** ✅ Done.
Settings via contributes.configuration applied live, confidence filtering, status bar, first-of-burst notification, packageable `.vsix`, CI job that runs the parity test and uploads the artifact. Marketplace/Open VSX publish is a credentials step, not yet wired.

**M5. Enrichment and Run/Debug capture (parity stretch).** Deferred.
Actuator and LLM in TS; debug adapter tracker capture (5.4); evaluate jdt.ls based PSI-equivalent enrichment. Exit: confidence upgrades demonstrably improve on at least the highest value checks, or a documented decision to stay text-only.

---

## 14. Effort Estimate

Assuming one developer fluent in TypeScript, no JVM dependency, MVP without PSI enrichment:

- M0 engine port + parity harness: about 1 to 1.5 weeks. This is the bulk of the real work and the most important to get right.
- M1 skeleton + paste: a few days.
- M2 to M3 (capture core and UI): about 1.5 to 2 weeks.
- M4 packaging and robustness: a few days to a week.
- M5 enrichment and debug capture: open ended; weeks if jdt.ls integration is pursued, near zero if deferred.

MVP through M4 is roughly **3 to 4 weeks**. Source aware enrichment is the long pole and is deliberately deferred. The estimate is similar to the rejected shared-core route: the JVM/CLI plumbing that route needed is replaced here by the engine port and parity harness.

---

## 15. Open Questions and Decisions to Revisit

1. **Parity suite scope.** Does card-level equality (ids, phase, dedup) suffice, or do we also assert excerpt text byte-for-byte? Leaning card-level plus excerpt presence, not exact excerpt bytes, since segmentation offsets may legitimately differ. Decide in M0.
2. **File watcher vs polling for `build/` outputs.** VS Code `files.watcherExclude` likely hides `build/`. Verify whether an explicit watcher fires; default to polling if not (matches IntelliJ).
3. **Card surface: Webview vs Tree+hover.** Webview is closest to parity but heavier; decide after a UX spike in M1.
4. **YAML parser choice.** `js-yaml` is the obvious pick; confirm it handles the rule file's constructs and pin it.
5. **How much PSI parity is worth rebuilding.** Quantify which enrichment checks actually change outcomes on real logs before investing in jdt.ls.
6. **Unified vs independent versioning** for the two artifacts. Default unified (they ship from one tag); revisit only if they must diverge.

---

## 16. Decisions Log

- 2026-06-25: Plan created, originally recommending a shared headless Java core (Option A) pending a JVM-dependency decision.
- 2026-06-25: **Decision reversed by the team.** Chose a native TypeScript engine over the shared Java core. Rationale: for VS Code a pure TypeScript extension is the simpler, more native integration with a lighter install and no process lifecycle to manage; the team accepts porting the logic. The recurring drift cost of a two-language engine is contained by keeping `spring-boot-rules.yaml` and the test fixtures as single shared sources and gating CI with a cross-engine parity suite (built first, in M0). The JVM dependency is no longer in play, so its open question is closed.
- 2026-06-25: **Single repo, added artifact.** The VS Code extension is built in this repository and shipped as an additional release artifact (`.vsix` alongside the IntelliJ `.zip`) from the same tags. No separate repository.
- 2026-06-25: **M0–M4 implemented.** Engine ported to TypeScript and verified card-for-card against the Java golden over all 93 corpus logs; capture layer (test-results watcher, log tailer), history tree, webview card, settings, status bar, and `.vsix` packaging all in place. 139 TS tests + the Java golden assertion both green; CI runs both and fails on cross-engine drift. Open question 1 (parity scope) resolved: card fields exact, excerpt presence-only. Marketplace/Open VSX publishing and M5 enrichment remain.
