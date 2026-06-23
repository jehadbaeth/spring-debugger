# Accuracy and Performance Analysis: v0.3.0

**Release theme:** LLM fallback (M13) and real-world corpus expansion (15 → 28 logs).
**Date:** 2026-06-23

This release adds the optional local-Ollama fallback and nearly doubles the real-world test
corpus. Testing against the new real logs drove four concrete engine fixes, which is the
point of the exercise: real data, not synthetic fixtures, found the gaps.

---

## 1. Real-world corpus

| Metric | v0.2.0 | v0.3.0 |
|---|---|---|
| Logs in corpus | 15 | 28 |
| Expected matches correct | 13 / 13 | 26 / 26 |
| False positives | 0 | 0 |
| Acknowledged format gaps (NO_MATCH) | 2 | 2 |

The 13 new logs were sourced from public pages (GitHub issues, blog posts, Q&A threads) and
cover error families the corpus had not exercised before: Jackson infinite recursion (7.2)
and missing-default-constructor (7.4), DataIntegrityViolation (4.13), 404 (5.1), bean
validation (5.5), property bind failure (3.4), YAML syntax (3.5), no embedded DB (4.1),
HikariCP exhaustion (4.4), port in use (1.8), generated security password (6.2), and two
MapStruct cases (13.1, 13.4).

The two NO_MATCH cases (RW-004, RW-005) remain documented limitations: both use the legacy
"expected at least 1 bean" phrasing instead of "required a bean of type", which rule 2.1
deliberately does not key on.

### Sourcing honesty

Two of the 13 carry caveats, recorded in the test's source notes:

- **NEW-004 (404):** the canonical NoHandlerFoundException stack pattern, reconstructed from
  several public sources (the single best page returned HTTP 403). The exception class and
  log shape are real Spring framework output, not invented, but it is not a verbatim copy of
  one page.
- **NEW-012 (MapStruct unmapped target):** the error text is the real warning from the cited
  GitHub issue; the surrounding Gradle build framing is illustrative.

All other 11 are taken from a single verified public page each.

---

## 2. Engine fixes the real logs exposed

| # | Gap found | Fix |
|---|---|---|
| 1 | Logs with no `Caused by:` chain (inline `nested exception is`, or a bare top-level exception) produced no signal | Extractor now falls back to the deepest `nested exception is`, then to a top-level exception line. The canonical `Caused by:` chain still wins when present |
| 2 | NEW-008 (cannot determine embedded DB driver) misfired to the generic 1.3 wrapper | Rule 1.3 (BeanCreationException) moved late in the catalog so specific banner rules win first |
| 3 | NEW-003 (DataIntegrityViolation) did not match: the Spring exception is the outer wrapper, the deepest cause is the JDBC driver exception | Rule 4.13 now keys on the wrapper text |
| 4 | NEW-005 (bean validation) is logged inside `Resolved [...]` with no cause chain | Rule 5.5 now keys on the wrapper text |

A side effect of fix 1: RW-001 now resolves to rule 1.13 (its true deepest cause,
BeanCurrentlyInCreationException) instead of the 2.7 banner-text match. Both are correct
circular-dependency diagnoses; 1.13 is the same resolution SOL-008 already used. The
expectation was updated to reflect the more precise behaviour.

---

## 3. LLM fallback (M13)

The fallback is off by default and fires only when no rule matches. It is local-Ollama only;
cloud providers are deliberately not built, because error logs can carry secrets (cf. rule
3.12) and sending them off-machine is an exfiltration path.

What is verified automatically:

- **Safety contract:** a garbage, truncated, or incomplete model reply yields no card rather
  than a malformed diagnosis (unit-tested with several bad-input shapes).
- **Protocol:** request envelope construction and response-field extraction (unit-tested).
- **Gating:** when disabled, the pipeline stays on the pure offline path; the other 118
  tests behave identically.

What is not covered by automated tests: a live round-trip to a running Ollama instance (no
model is available in CI). LLM cards are labelled `llm` at MEDIUM confidence so they always
rank below a rule match and are clearly distinguishable.

---

## 4. Test suite

118 tests passing (up from 102 in v0.2.0): +13 real-world cases, +3 extractor fallback
cases. Breakdown of the non-fixture suites:

| Suite | Tests |
|---|---|
| ClassifierFixtureTest | 45 |
| RealWorldAccuracyTest | 28 |
| LlmDiagnosisEngineTest | 7 |
| PsiEnricherTest | 6 |
| ActuatorReaderTest + ActuatorEnricherTest | 9 |
| LogExtractorTest | 8 |
| OllamaHttpClientTest | 4 |
| BuildOutputAnalyzerTest | 5 |
| DiagnosisPipelineTest | 3 |
| RuleCatalogTest | 3 |

---

## 5. Honest limitations carried forward

- M6 live tap firing in a running IDE sandbox is still unverified (see PLAN open items).
- LLM live round-trip is unverified (no model in CI).
- Rule 13.8 (MapStruct null-mapping) stays TODO: neither the signal nor PSI can prove the
  null-value-strategy claim.
- `ActuatorReader.effectivePropertySource` remains scaffolding with no enricher consumer yet.
