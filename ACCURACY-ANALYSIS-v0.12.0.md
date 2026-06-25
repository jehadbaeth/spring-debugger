# Accuracy and Performance Analysis: v0.12.0

**Release theme:** the VS Code extension gains the two features that were missing versus IntelliJ,
source-aware enrichment and the local LLM fallback. The rule engine was already at parity; this
closes most of the remaining feature gap.
**Date:** 2026-06-25

## 1. What this release adds (VS Code)

The IntelliJ plugin is functionally unchanged (version bumped for unified release). The VS Code
extension gains:

- **Source enrichment (PSI equivalent).** The pure PsiEnricher decision logic is ported verbatim. In
  IntelliJ it reads PSI; in VS Code it resolves classes through the stable
  `executeWorkspaceSymbolProvider` (backed by `redhat.java` when installed) plus a lightweight source
  parser, then names the exact missing type, where it lives, the best-fit stereotype, and which bean
  needed it, upgrading the card to HIGH.
- **Actuator + property-precedence enrichment.** Ported 1:1. For a runtime error against a live app,
  they confirm `/actuator/health` and report the effective property source. Off by default
  (`springDebugger.actuator.enabled`) since they call your app.
- **Ollama LLM fallback.** When no rule matches, a local Ollama model is asked for a best-effort
  one-sentence diagnosis and fix, with the same safety contract as IntelliJ (a malformed reply
  surfaces nothing). Off by default. Local only, never a cloud provider.

## 2. How accuracy is protected

- **Pure logic ported verbatim against the Java unit tests.** ActuatorReader, all three enricher
  decision paths, `LlmDiagnosisEngine.parseCard` (the safety contract), and the Ollama protocol
  helpers each have a TypeScript test mirroring the Java test 1:1.
- **The rule-engine golden is untouched.** Enrichment runs only on a new async `diagnoseAllEnriched`
  path; the sync `diagnoseAll` and `parity/golden.json` are byte-identical to v0.11.0, so the
  cross-engine parity guarantee is intact. CI still asserts both.
- **No confidently-wrong upgrades.** Class resolution bails on ambiguity: a fully qualified name
  requires an exact, unambiguous package match; a bare simple name requires exactly one candidate.
  Otherwise enrichment no-ops and the rule card stands.

## 3. Measured coverage

| Engine | Tests | Notes |
|---|---|---|
| Java (IntelliJ) | 203 | unchanged |
| TypeScript (VS Code) | 195 | 94 parity + 101 unit (engine, enrichers, LLM, watchers, history, glue, source parser) |

## 4. Honest gaps that remain

- **Library-type `@Bean` branch.** The PsiEnricher path that tells you to declare an `@Bean` for a
  third-party type cannot fire from VS Code resolution: a source/symbol scan cannot prove a type is
  library code (not-found is not the same as library). All other enricher branches work. Without the
  Java extension installed, source enrichment no-ops entirely and rules fire at base confidence.
- **LLM placement differs deliberately.** Java calls the LLM per block/phase; VS Code fires it at
  most once per `diagnoseAll`, only when no rule matched, to avoid a burst of slow Ollama calls on
  every poll. The diagnosis quality is the same; only the call pattern differs.
- **Still pending (not in this release):** Run/Debug-adapter capture, and Marketplace / Open VSX
  publishing (a credentials step). The `.vsix` is attached to the release for manual install.

## 5. Performance

Enrichment adds work only when a rule already matched and a context is configured. Source
enrichment uses the symbol provider (indexed, fast) and bails early on ambiguity; the app-package
scan is memoized per session. Actuator and LLM calls are off by default and hard-bounded by timeouts
(2s actuator, 30s Ollama) so a down app or a cold model cannot stall a poll. The full TS suite runs
in well under a second.
