# Accuracy and Performance Analysis: v0.6.0

**Release theme:** multi-error extraction from noisy integration logs, plus a rule found by
running a real app.
**Date:** 2026-06-24

## 1. What changed

Integration runs (e.g. Robot Framework hitting a live app) throw many server-side errors into
one long-running log. The engine previously pulled a single deepest cause and emitted one card.
v0.6.0 adds:

- `StackTraceSegmenter` — splits a buffer into per-error blocks (single-error logs stay one
  block, preserving all prior behaviour).
- `ConsoleDiagnoser.diagnoseAll` — diagnoses each block and de-duplicates by rule + diagnosis.
- History grouping — repeats collapse to one row with a `×N` count.
- Burst-quiet balloons — only the first error of a burst notifies; the rest update history
  quietly, so negative tests do not flood the UI.
- Mid-run analysis for the Gradle/Maven panel — `ExternalBuildOutputTap` now analyses while a
  `bootRun` task is still running, not only at task end (the load-bearing fix for this case).

## 2. Validated against a real app

Per the plan's suggestion, a minimal Spring Boot 3.2 app was built, broken, and run:

- **Missing constructor-injected bean** → the failure analyzer prints a banner and suppresses
  the stack, so the existing rule 2.1 (which needs a `Caused by` class) matched nothing. This
  gap was real and is now fixed by **rule 2.13** (keyed on the banner Description); the genuine
  captured log is its fixture.
- **App started, then `/npe`, `/ise`, `/missing`, and a bad POST were hit** → a genuine noisy
  console with a NullPointerException, an IllegalStateException, a 404, and a validation
  failure interleaved. `diagnoseAll` digs the actionable, rule-covered error (5.5 validation)
  out of the noise; this log is a real-world fixture (LIVE-002).

Honest note: of the four runtime errors in that capture, only the validation maps to a rule;
the NPE and IllegalStateException have no specific rule, which is the correct behaviour (the
plugin surfaces the errors it can actually act on).

## 3. Catalog and tests

- 56 rules, all DONE (added 2.13).
- 181 tests passing. New pure/unit coverage: `StackTraceSegmenterTest`, `ConsoleDiagnoserTest`
  (multi-error + dedupe), `DiagnosisHistoryServiceTest` (grouping + counts), plus the
  `RealWorldMultiErrorTest` over the genuine capture.

## 4. Honest limitations

- The mid-run external-tap firing and the history-grouping UI are IDE-coupled; the segmentation,
  dedupe, grouping, and diagnosis logic are unit-tested, but live firing needs a hands-on check.
- Robot Framework output itself is not parsed: the user confirmed the actionable error lives in
  the app console, so parsing Robot output was deliberately not built.
