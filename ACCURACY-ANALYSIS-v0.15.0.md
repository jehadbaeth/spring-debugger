# Accuracy and Performance Analysis: v0.15.0

**Release theme:** the code convention engine (Javadoc + Robot Framework + eight Spring Boot layering
checks, shipped in v0.13.0 and v0.14.0 for IntelliJ) is now mirrored in the VS Code extension. No
IntelliJ-side rule behavior changes in this release.
**Date:** 2026-07-10

## 1. What is new

VS Code gets its own TypeScript convention engine, built to match the Java one check for check:

- A `ConventionCatalog` YAML loader and `ConventionRule`/`ConventionCheck`/`CheckRegistry` layer,
  structurally equivalent to the IntelliJ `ConventionInspection` machinery.
- A hand-rolled regex based Java member parser (`java-members.ts`) for fields, methods, parameters,
  classes, and annotations, since VS Code has no PSI to lean on. It supports arbitrary formatting,
  including single-line-packed declarations, not just one-statement-per-line source.
- A near-verbatim TypeScript port of the Robot Framework suite parser (`robot-suite.ts` /
  `robot-suite-parser.ts`).
- All 13 checks (Javadoc, the four Robot rules, and the eight Spring Boot rules) ported and run
  through the same fixture strings and expected anchors as the Java test suite, so both IDEs are
  provably checking the same thing, not just structurally similar code.
- `DiagnosticCollection` wiring in `extension.ts`: diagnostics publish on open, re-run on edit, and
  clear on close, gated by a `springDebugger.conventions.enabled` kill switch and a per-rule
  `springDebugger.conventions.ruleOverrides` map, mirroring `SpringDebuggerSettings`'s
  `conventionsEnabled` / `conventionRuleEnabled` semantics exactly.
- The canonical `conventions.yaml` (already the single source of truth for the IntelliJ plugin) is
  bundled into the extension at package time via `copy-assets.js`, so both IDEs read the same rule
  definitions and severities.

## 2. Test coverage

39 new tests: 3 for catalog loading, 28 for the 9 Java-based checks (Javadoc + Spring Boot), 8 for
the 4 Robot checks, plus 8 for the member parser itself and 6 integration tests proving the
`DiagnosticCollection` wiring (publish, re-run, clear-on-close, non-Java/Robot files ignored,
per-rule override, feature-level kill switch). Full VS Code suite: 249/249 passing. `tsc --noEmit`
clean. `npm run bundle` succeeds.

## 3. Known caveats

- **No debounce on `onDidChangeTextDocument`.** Convention checks re-run on every keystroke in open
  `.java`/`.robot` files. Each check is a cheap regex scan over already-masked text, so this is
  expected to be fine in practice, but it has not been measured against a very large file (many
  thousands of lines) under rapid typing.
- **Deferred, matching the IntelliJ plan (CONVENTIONS-PLAN.md section 11):** directory-structure and
  test-data-naming Robot rules, any cross-file check (needs symbol resolution), and quick fixes (a
  Javadoc-stub insertion action). None of these are required for this milestone.
- **No dedicated Conventions settings UI in VS Code**, same as IntelliJ: the catalog's own `enabled`
  flag plus the two new settings above are considered sufficient for now.
- **VS Code has no native "inspect before commit" gate**, unlike IntelliJ's commit-time inspection
  profile, so the practical trigger for seeing these diagnostics in VS Code is opening/editing a
  file, not a commit hook.
