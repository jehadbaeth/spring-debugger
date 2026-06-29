# Accuracy and Performance Analysis: v0.13.0

**Release theme:** a second analysis capability in the IntelliJ plugin, code convention validation,
in the SonarLint/FindBugs mould with rules we define. Ships a Javadoc rule and four Robot Framework
integration test rules. The log diagnoser is unchanged.
**Date:** 2026-06-30

## 1. What is new

A proactive, source-file analysis layer separate from the reactive log diagnoser. It reads files and
flags convention violations as native inspection warnings: on the fly as you type, in *Analyze >
Inspect Code*, and at commit time (IntelliJ's commit dialog inspection step). Driven by its own
catalog `conventions.yaml`; each rule turns on and off via an `enabled` flag and a per-rule override
in Settings, with a whole-feature kill switch.

Architecture: `ConventionCatalog` (separate from the diagnoser's `RuleCatalog`) → parameterized
`checkType` implementations resolved through `CheckRegistry` → one umbrella `ConventionInspection`
(`LocalInspectionTool`) driven through `buildVisitor`, so the on-the-fly, batch, and commit triggers
all come from a single registration.

## 2. Rules shipped

| Rule | checkType | Enforces |
|---|---|---|
| JAVADOC_METHOD | javadocRequired | Public methods have a Javadoc comment (skips overrides, accessors, constructors) |
| ROBOT_METADATA_REQUIRED | robotMetadataRequired | Suite `*** Settings ***` declares Test ID, Test Description, Pass-Fail Criteria |
| ROBOT_TEST_ID_FORMAT | robotTestIdFormat | `Metadata Test ID` matches `T-(RE\|FG\|CA)-NNNN` (team-confirmed scope set) |
| ROBOT_TESTCASE_DOC | robotTestCaseDoc | Every test case has `[Documentation]` |
| ROBOT_TESTCASE_TAGS | robotTestCaseTags | Every test case has a requirement tag (`REQ-...`) |

The Robot checks parse a single `.robot` suite with a pure `RobotSuiteParser` (two-space/tab
separators, `#` comments, `...` tag continuations, case-insensitive `*** headers ***`). The
metadata-required check only fires on files with a `*** Test Cases ***` section, so resource,
keyword, and variable `.robot` files are left alone.

## 3. Accuracy notes

- **Test ID scope set.** The source convention document is internally inconsistent: its normative
  section lists scopes SYS/ST/CA/FG/RE, while its own examples use `T-API-1909` and `T-ID-007`. The
  team confirmed the real set is RE, FG, CA, so the default pattern is `T-(RE|FG|CA)-\d{4}`. It is a
  rule param, so the scope set and digit count can change without code changes. Both the doc's
  `T-API-1909` and a well-formed-but-wrong-scope `T-ST-0001` are correctly rejected.
- **False-positive guard.** The metadata-required rule is gated on the presence of a Test Cases
  section so shared keyword/resource files are never flagged.
- **Javadoc rule overlaps** with IntelliJ's built-in "Missing Javadoc" inspection and Checkstyle; it
  is scaffolding that proved the engine. The differentiated value is the Robot rules and future
  team-specific rules.

## 4. Coverage

| Engine | Tests | Notes |
|---|---|---|
| Java (IntelliJ) | 233 | +29 for the convention engine (catalog, Javadoc, Robot parser, four Robot checks, inspection highlighting) |
| TypeScript (VS Code) | 196 | Unchanged; conventions are IntelliJ only this release |

## 5. Known limitation (honest)

The `.robot` highlighting path is unit-tested on plain-text PSI, but the test fixture auto-detects
`.robot` as text, so it cannot prove the real-IDE file-type association. v0.13.0 registers `.robot`
as a secondary plain-text file type so the checks run without a dedicated Robot plugin; if such a
plugin is installed, ours yields to it and the checks do not run on `.robot`. Confirming the live
squiggle in a running IDE is the one item this release does not prove automatically.

## 6. Performance

No impact on the log diagnoser. The convention inspection runs per file under the platform's normal
inspection scheduling; each check is a bounded single-file pass (PSI traversal for Java, one linear
text parse for Robot), no IO and no cross-file resolution.
