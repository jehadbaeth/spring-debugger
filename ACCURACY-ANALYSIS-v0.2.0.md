# Accuracy and Performance Analysis: v0.2.0

**Release theme:** build-tap wiring (M6) and the enrichment layers (M8 PSI, M9 Actuator).
**Date:** 2026-06-23

This release adds detection sources beyond the run/test console logs. It does not change
the offline rule classifier, so the real-world offline corpus result is unchanged from
v0.1.3. The new value is in two places the offline corpus cannot measure: build output and
IDE/runtime enrichment.

---

## 1. Offline corpus (unchanged)

The real-world corpus is still 15 logs. The classifier runs them directly, without any
enrichment context, so the numbers carry over from v0.1.3 with no regression.

| Metric | v0.1.3 | v0.2.0 |
|---|---|---|
| Logs in corpus | 15 | 15 |
| Expected matches correct | 13 / 13 | 13 / 13 |
| False positives | 0 | 0 |
| Acknowledged format gaps | 2 | 2 |

Corpus expansion to 25+ logs is scheduled for the next release alongside the LLM fallback.

---

## 2. What v0.2.0 actually changed

### M6: build taps now wired (was dead code)

Before v0.2.0 the `BuildOutputTap` was never registered, so compile-phase rules
(6.4, 10.x, 13.x) could only fire if build text leaked into a run or test console.
v0.2.0 registers two taps:

- `BuildOutputTap` via the `compiler.task` extension for IntelliJ internal JPS builds.
- `ExternalBuildOutputTap` via the external-system listener for delegated Gradle/Maven
  builds, which is the default build path for most Spring Boot projects.

Both share `BuildOutputAnalyzer`, a pure core that is unit-tested with canned build output.

**Verification status:** classification is fixture-verified, the analyzer is unit-tested.
Live firing in a running IDE sandbox for a delegated Gradle build is **not yet confirmed**;
it is the one open item before M6 can be called fully DONE.

### M8: PSI enrichment

For non-HIGH rule matches, `PsiEnricher` confirms structural claims against project source:

| Rule(s) | Enrichment | Effect when confirmed |
|---|---|---|
| 13.3, 13.4 | type is a `@Mapper` interface | upgrade to HIGH, name the mapper |
| 2.x | missing-bean type has no Spring stereotype | upgrade to HIGH, name the class |
| 2.x | annotated type lives outside the `@SpringBootApplication` scan tree | upgrade to HIGH, give the package and fix |

On any uncertainty the offline card is returned unchanged. Decision logic is unit-tested
with stubbed `ClassFacts` (no live IDE).

### M9: Actuator enrichment

For non-HIGH RUNTIME cards, `ActuatorEnricher` queries `/actuator/health` on the detected
app port. If the app reports DOWN it names the failing component and upgrades to HIGH. This
is strictly additive and has a deliberately narrow trigger surface (live app + Actuator +
uncertain runtime match), documented as such. Parsing and decision logic are unit-tested.

---

## 3. Test suite

| Suite | Tests |
|---|---|
| ClassifierFixtureTest | 45 |
| RealWorldAccuracyTest | 15 |
| BuildOutputAnalyzerTest | 5 |
| PsiEnricherTest | 6 |
| ActuatorReaderTest | 5 |
| ActuatorEnricherTest | 4 |
| DiagnosisPipelineTest | 3 |
| LogExtractorTest | 5 |
| RuleCatalogTest | 3 |
| **Total** | **91** |

All passing. The enrichment and build-analysis cores are covered by unit tests that run
without a live IDE; the IDE-coupled adapters (PSI index access, real HTTP, tap registration)
are validated by plugin-structure verification and a pending manual sandbox check for M6.

---

## 4. Honest limitations

- M6 live firing is unverified in a sandbox run (see above).
- M9's `ActuatorReader.effectivePropertySource` is implemented and tested but has no enricher
  consumer yet; it is scaffolding for a future property-precedence enricher, not an
  end-to-end feature.
- Enrichment only affects live IDE runs; it does not (and cannot) change the offline corpus
  numbers, which is why those are reported separately.
