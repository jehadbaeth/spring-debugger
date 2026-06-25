# Accuracy and Performance Analysis: v0.11.0

**Release theme:** a VS Code extension, built in this repo as a second artifact, sharing the rule
catalog with the IntelliJ plugin and proven equivalent by a cross-engine parity test.
**Date:** 2026-06-25

## 1. What this release adds

A native TypeScript VS Code extension under `vscode-extension/`, shipped as a `.vsix` alongside the
IntelliJ `.zip` from this same tag. It brings the same promise: watch run, test, and build output and
surface a two-sentence diagnosis when an error is recognised. The capture paths are the
terminal-agnostic ones the IntelliJ plugin already proved: test-results watching, log-file tailing
with run-boundary awareness, and a paste command.

The IntelliJ plugin is unchanged in behaviour. Its rule catalog and 203 tests are still the
authority; this release does not touch them beyond the version bump and the new parity anchor test.

## 2. How accuracy is guaranteed across two engines

The risk of a second engine is that the two drift and diagnose the same log differently. This is
controlled, not hoped for:

- **One rule catalog.** `spring-boot-rules.yaml` is read by both products and copied into the `.vsix`
  at build time. It is never forked.
- **A golden generated from the shipped Java diagnoser.** `ParityGoldenTest` runs the real
  `ConsoleDiagnoser.diagnoseAll` over all 93 fixture and real-world logs and writes
  `parity/golden.json`. It is generated from the actual code path the IntelliJ plugin runs, not a
  replica, so it certifies shipped behaviour.
- **The TypeScript engine is asserted against that golden.** The TS parity test runs the ported
  engine over the same 93 logs and asserts identical cards: `ruleId`, `phase`, `diagnosis`, `fix`,
  and `confidence` exact; `excerpt` checked for presence only (it is raw passthrough, so byte
  comparison would add false failures without testing behaviour).
- **CI runs both.** The Java job asserts the golden; the new `vscode-extension` job runs the TS
  parity test. Either side drifting fails the build.

Result over the 93-log corpus: **the TypeScript engine reproduces the Java engine card for card, 0
divergences.**

## 3. Measured coverage

| Engine | Tests | Notes |
|---|---|---|
| Java (IntelliJ) | 203 | unchanged, includes the new `ParityGoldenTest` |
| TypeScript (VS Code) | 149 | 94 parity + 55 unit (engine, watchers, history, card, glue) |

Card confidence distribution across the corpus: 90 HIGH, 8 MEDIUM, 0 LOW. Because the default
`minimumConfidence` is MEDIUM, the background filter hides nothing on the current corpus.

The extension glue (`activate`, commands, config wiring, webview, history tree, status bar,
background filter and notification) runs under a mocked `vscode` host in the test suite, so it is
exercised rather than assumed.

## 4. Honest scope: engine parity, not full feature parity

The TypeScript engine is rule-only. Three things the IntelliJ plugin does at runtime are **not** in
this release and are deferred:

- **Enrichment** (PSI source inspection, Actuator health, property precedence). PSI has no VS Code
  equivalent and is the largest gap; Actuator and property precedence are straightforward Node ports
  not yet done.
- **LLM (Ollama) fallback.** Not ported; its settings are intentionally absent so the UI does not
  offer a dead control.

Consequence: on the same input, VS Code may show a diagnosis at lower confidence or with less
project-specific detail than IntelliJ. Today the corpus has no LOW-confidence cards, so the default
threshold hides nothing, but that changes if enrichment-dependent confidence or LOW rules arrive.

## 5. Performance

The VS Code capture paths poll (test results every 4s, log files every 1.5s), the same model the
IntelliJ plugin uses because build output directories are commonly excluded from editor file
watchers. Tailing reads only the byte delta since the last poll, capped at a 200k tail buffer per
file, and diagnoses only the latest run slice, so an appending multi-run log does not grow the work.
The full TS suite (including 94 parity diagnoses over the corpus) runs in well under a second.

## 6. Not yet wired

- Marketplace and Open VSX publishing (a credentials step).
- A live `@vscode/test-electron` smoke test for the real webview and tree rendering and the watcher
  timers firing in a host. Everything reachable without a display is covered.
