# Code Convention Validation: Implementation Plan

**Status:** proposed, not started
**Scope of this document:** the design and incremental build plan for a second analysis capability
in the plugin, alongside the existing log diagnoser: in-IDE validation of team code conventions, in
the SonarLint / FindBugs mould, with rules we define and can turn on and off.
**Date:** 2026-06-26

---

## 1. Goal

Add a static analysis layer that reads source files and flags violations of code conventions
directly in the editor, the way SonarLint and the old FindBugs plugin do, except the rules are ours.

The first rule is **Javadoc required on methods**. It is deliberately simple. Its job is to build and
prove the whole machine (catalog, check engine, inspection wiring, on and off toggles, the three
triggers, tests) so that adding the real value later, the Robot Framework convention and other team
specific rules, is authoring work and not plumbing.

This is a second engine under the same plugin. It does not touch the log diagnoser, its rule catalog,
or its parity golden.

---

## 2. What this is and is not

**It is** an in-plugin static analysis feature: rules evaluated against source files, violations shown
as native inspection highlights, all inside the IDE. No external jar, no CLI, no git hook, no script.

**It is not** a port of the diagnoser. The diagnoser is reactive: it captures run, test, and build
*output* and matches text signals. Conventions are proactive: they read your *files* and report at a
line and range. Different input, different trigger, different output. They share philosophy (a
declarative catalog we author, plain language phrasing) but not the pipeline.

### Honest framing

For generic Java Javadoc, IntelliJ already ships a "Missing Javadoc" inspection and Checkstyle does
this too. So do not judge the feature by the first rule. The first rule is scaffolding. The
defensible value is the combination those tools do not give in one place: team specific rules,
multi language (Robot Framework is not Java, no Java linter touches it), and one plain language UX we
control. That value shows up when the Robot and team rules land on top of the rails this plan builds.

---

## 3. Decisions locked

| Decision | Choice | Why |
|---|---|---|
| Surface | In-plugin inspection only | The SonarLint / FindBugs model the user asked for. No external runtime. |
| Authorship | Single author, baked into the plugin | Only the user edits rules; they ship in plugin resources like `spring-boot-rules.yaml`. |
| First rule | Javadoc required on methods | Trivial check, so focus stays on the rails. |
| Reach for v1 | IntelliJ first | The inspection and commit analysis APIs do this natively. VS Code mirror is a later phase (section 11). |
| On and off | Per rule `enabled` flag in our catalog | The user explicitly wants a catalog where rules turn on and off. |
| File scope | Single file, current file content only | Pure and deterministic. Cross file checks are deferred (section 11). |

---

## 4. Architecture

Four parts. Three are new code; the inspection registration is the only IntelliJ specific glue.

```
conventions.yaml  (resource, we author)
        │  loaded by
        ▼
ConventionCatalog ──► list of ConventionRule (id, checkType, enabled, params, message, fix, severity)
        │  consumed by
        ▼
ConventionCheck registry  ──► one CheckType implementation per checkType
   (e.g. JavadocRequiredCheck)         takes a PsiFile + rule params, returns List<Violation>
        │  driven by
        ▼
ConventionInspection (LocalInspectionTool)  ──► for the file being inspected, runs every enabled
                                                rule whose checkType applies to that file type,
                                                emits a ProblemDescriptor per Violation
```

### 4.1 The catalog: `conventions.yaml`

A new resource, separate file, separate from `spring-boot-rules.yaml`. Each entry is one rule. The
`enabled` flag is the on and off switch.

```yaml
rules:

  - id: "JAVADOC_METHOD"
    name: "Javadoc required on methods"
    checkType: "javadocRequired"
    enabled: true
    severity: WARNING        # WARNING | WEAK_WARNING | ERROR (maps to IntelliJ highlight severity)
    appliesTo: [java]        # file types this rule can run against
    params:
      visibilities: [public] # only public methods require Javadoc
      skipOverrides: true    # methods annotated @Override are exempt
      skipAccessors: true    # trivial getters and setters are exempt
      skipGetterSetterNames: true
    message: "Public method '{{method}}' is missing a Javadoc comment."
    fix: "Add a Javadoc comment above '{{method}}' describing what it does, its parameters, and its return value."
    status: DONE
```

Schema fields:

- `id` unique key, used in tests and in the settings UI.
- `checkType` selects the implementation. Unknown checkType is a load error, not a silent skip.
- `enabled` the toggle. A disabled rule is loaded but never evaluated.
- `severity` maps to IntelliJ `ProblemHighlightType` / `HighlightSeverity`.
- `appliesTo` file types; the inspection skips a rule whose `appliesTo` excludes the current file.
- `params` free form map passed to the check implementation. Each checkType documents its own params.
- `message`, `fix` plain language, with `{{placeholder}}` interpolation filled by the check.
- `status` DONE means active and validated, mirroring the diagnoser convention.

### 4.2 Loader: `ConventionCatalog`

Mirrors `RuleCatalog`: load the YAML resource with SnakeYAML into a raw map, build immutable
`ConventionRule` objects, expose `all()`, `enabled()`, `byId()`. Reuses the existing `snakeyaml`
dependency. No new library.

### 4.3 Check engine: `CheckType` and `JavadocRequiredCheck`

```java
interface ConventionCheck {
    String checkType();                       // "javadocRequired"
    List<Violation> check(PsiFile file, ConventionRule rule);
}

record Violation(PsiElement anchor, String message, String fix) {}
```

`JavadocRequiredCheck` walks the PSI for `PsiMethod` elements, applies the rule params
(visibility filter, skip overrides, skip accessors), and for each method with no `getDocComment()`
returns a `Violation` anchored at the method name identifier so the squiggle lands on the name, not
the whole body. This is pure PSI traversal: deterministic, no network, no cross file resolution.

A small registry maps `checkType` string to implementation. v1 has exactly one entry. Adding a
checkType later (the Robot one) is: implement the interface, register it, author rules that use it.

### 4.4 Inspection surface: `ConventionInspection`

One `LocalInspectionTool` registered in `plugin.xml` under `localInspection`. Its
`buildVisitor` (or `checkFile`) loads the catalog, filters to enabled rules whose `appliesTo`
matches the file, dispatches each to its `ConventionCheck`, and reports every `Violation` via
`ProblemsHolder.registerProblem` with the rule's severity and the message and fix text.

Registering a single umbrella inspection (rather than one inspection class per rule) is the right
call here because rule on and off lives in our catalog, not in IntelliJ's inspection profile. One
inspection, many catalog driven rules. This is documented as a deliberate divergence from the
"one inspection per rule" idiom, chosen so the catalog stays the single source of truth for what is
on.

---

## 5. The three triggers (all native, no extra code)

A single `LocalInspectionTool` gives all three for free. This is exactly why the in-plugin inspection
model is the right fit for the user's "manual trigger or on commit" idea.

1. **On the fly, as you type.** IntelliJ runs local inspections continuously on the open file. The
   squiggle appears under the offending method without any action.
2. **Batch, on demand.** *Analyze > Inspect Code* (or *Run Inspection by Name*) runs the same
   inspection across a scope the user picks (file, module, changed files, whole project). This is the
   manual trigger.
3. **On commit.** The IntelliJ commit dialog has a built in "Analyze code" / inspection step over the
   VCS changed files. Our inspection participates automatically. This is the "check staged files on
   commit" idea, done natively, no git hook.

Nothing extra is registered for triggers 2 and 3. They come from registering the inspection.

---

## 6. On and off, surfaced to the user

Two layers, both honest:

1. **Catalog `enabled` flag** the authored default, shipped in `conventions.yaml`. This is the
   primary switch the user asked for.
2. **Settings panel** the existing `SpringDebuggerSettingsConfigurable` under *Tools > Spring Boot
   Debugger* gets a "Conventions" section listing each rule by name with a checkbox. The checked
   state persists in `SpringDebuggerSettings` (a new `Map<String,Boolean> conventionRuleEnabled` or a
   set of disabled ids). The inspection consults the persisted override, falling back to the
   catalog default. So the catalog sets the default and the user can flip any rule without editing
   YAML.

A whole feature kill switch (`conventionsEnabled`, default true) sits alongside the diagnoser's
`enabled` flag, so the entire convention layer can be turned off in one place.

---

## 7. Testing

The diagnoser uses fixture driven classifier tests plus a cross engine parity golden. Conventions in
v1 are IntelliJ only, so the parity golden does not apply yet (deferred with VS Code, section 11).
The standard IntelliJ approach replaces it:

1. **Catalog load test** (`ConventionCatalogTest`, plain JUnit) every rule parses, every `checkType`
   resolves to a registered implementation, no duplicate ids, `enabled` and `severity` valid.
2. **Inspection highlighting tests** (`JavadocRequiredCheckTest`, `BasePlatformTestCase` like the
   existing PSI tests) feed Java source fixtures through `myFixture.enableInspections` +
   `testHighlighting`, asserting the squiggle lands on exactly the methods that violate and skips the
   ones the params exempt (private, `@Override`, accessors). Positive and negative fixtures.
3. **Param coverage** one fixture per param branch (visibility filter on and off, skipOverrides on
   and off, skipAccessors on and off) so each knob is proven.

Fixtures live under `src/test/resources/conventions/` so they never collide with the diagnoser's
log fixtures. No test hardcodes a rule count that the catalog would break on growth.

---

## 8. Files to add or touch

New, IntelliJ side:

- `src/main/resources/rules/conventions.yaml` the catalog.
- `src/main/java/com/springdebugger/convention/ConventionRule.java`
- `src/main/java/com/springdebugger/convention/ConventionCatalog.java`
- `src/main/java/com/springdebugger/convention/ConventionCheck.java` (interface + Violation)
- `src/main/java/com/springdebugger/convention/CheckRegistry.java`
- `src/main/java/com/springdebugger/convention/checks/JavadocRequiredCheck.java`
- `src/main/java/com/springdebugger/convention/ConventionInspection.java`
- tests under `src/test/java/com/springdebugger/convention/` and fixtures under
  `src/test/resources/conventions/`.

Touched:

- `src/main/resources/META-INF/plugin.xml` register `localInspection` and, if used, an
  `inspectionGroup`.
- `SpringDebuggerSettings` add the per rule enabled overrides and the feature kill switch.
- `SpringDebuggerSettingsConfigurable` / `SpringDebuggerSettingsPanel` add the Conventions section.
- `README.md` document the new capability and the first rule.
- A new `ACCURACY-ANALYSIS-vX.Y.Z.md` on release, per the standing practice.

Not touched: anything under `tap`, `classifier`, `rule` (the diagnoser), `parity/golden.json`,
`spring-boot-rules.yaml`. The two engines stay isolated.

---

## 9. Build increments (small, atomic, in order)

Each step is its own commit, each leaves the build green.

1. **Catalog skeleton.** Add `conventions.yaml` with the one Javadoc rule, `ConventionRule`,
   `ConventionCatalog`, and `ConventionCatalogTest`. No inspection yet. Proves the catalog loads.
2. **Check engine.** Add `ConventionCheck`, `Violation`, `CheckRegistry`, `JavadocRequiredCheck`
   with unit tests over PSI fixtures (pure, no inspection registration). Proves the check logic.
3. **Inspection wiring.** Add `ConventionInspection`, register in `plugin.xml`, add the
   `testHighlighting` based tests. Proves the squiggle appears on the fly. After this step the three
   triggers all work.
4. **On and off in settings.** Add the persisted overrides and the Conventions settings section,
   with a test that a disabled rule produces no highlight. Proves the toggle.
5. **Docs and release.** README, accuracy analysis doc, version bump, release. (Ask before tagging.)

Only after this slice is green do we move to the Robot Framework rule, which is a new `checkType`
implementation plus authored rules extracted from the convention document the user will provide.

---

## 10. Risks and how this plan handles them

- **Overlap with built in Javadoc inspection.** Acknowledged. The first rule is scaffolding; value is
  in later rules. Stated plainly so it is not mistaken for the deliverable.
- **PSI threading.** Inspections run under a read action managed by the platform; the check does only
  PSI reads, no writes, no IO, so it is safe. The tests run on the platform test fixture which
  enforces this.
- **Catalog growth breaking tests.** No test asserts a fixed rule total; tests assert behaviour per
  fixture, as the diagnoser tests already do.
- **Scope creep into cross file checks.** Explicitly out of v1 (section 11). Single file keeps the
  engine pure and the first slice small.

---

## 11. Deferred, on purpose (not in v1)

- **Robot Framework rule.** The real target. Lands as a second `checkType` after the rails are proven
  and after the user shares the convention document to extract rules from.
- **VS Code mirror.** The diagnoser ships in both IDEs. Conventions start IntelliJ only. Mirroring
  means a TypeScript check engine (VS Code has no PSI, so it reuses the existing pure Java source
  parser) feeding a `DiagnosticCollection`, plus a parity golden over source fixtures for single file
  checks. Worth doing, but only after IntelliJ v1 proves the design. Note: VS Code git has no native
  "inspect before commit" gate, so the commit trigger is weaker there.
- **Cross file checks.** Anything needing symbol resolution across files. Non deterministic via IDE
  symbol providers (same bail on ambiguity issue as the diagnoser's enricher), so kept out until the
  single file rails exist, and kept out of any future parity golden.
- **Quick fixes.** A `LocalQuickFix` that inserts a Javadoc stub. Natural next step once violations
  render, but not required for v1.

---

## 12. Open questions for the user

1. Confirm IntelliJ first is acceptable, with VS Code as a later mirror (section 11).
2. Default severity for the Javadoc rule: WARNING (yellow) or WEAK_WARNING (greyer, less noisy)?
3. Should the Conventions settings section ship in v1 (step 4), or is the catalog `enabled` flag
   enough for now and the settings UI follows later?
