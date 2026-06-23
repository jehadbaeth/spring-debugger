# Accuracy Analysis — Spring Boot Debugger (living document)

**Last updated:** 2026-06-23  
**Versions covered:** 0.1.0 (initial) → 0.1.1 (phase filter, rule 13.4) → 0.1.2 (corpus expanded to 15) → 0.1.3 (corpus-driven rule changes)  
**Total rules:** 44 (43 DONE, 1 TODO — 13.8, deferred to M8/PSI)  
**Active rules at runtime:** 43 (only DONE rules fire)  
**Real-world log tests:** 15 (5 RW-series + 10 SOL-series)

> This is a single living document, not a per-version snapshot. Each release's
> changes are folded in and the numbers reflect the current `main`.

---

## Methodology

Real-world Spring Boot error logs were collected from publicly accessible sources:
- GitHub Issues (spring-projects/spring-boot, apache/shardingsphere, redisson, spring-cloud-config, spring-projects/spring-integration-samples)
- Blog tutorials (yawintutor.com, jvt.me, ggorantala.dev, Medium/@junjaboy)

Stack Overflow itself blocks automated fetching; logs from SO were obtained indirectly via blog posts and GitHub issues that reproduce the same errors verbatim.

Each log was saved as a test resource in `src/test/resources/real-world-logs/` and run programmatically through `LogExtractor` + `RuleBasedClassifier` in `RealWorldAccuracyTest`. Tests either assert the expected rule fires (MATCH) or that no rule fires (NO_MATCH with a documented reason).

---

## Results — All 15 Real-World Test Cases

| ID | Source | Error Type | SB Version | Phase | Expected Rule | Actual | Verdict |
|---|---|---|---|---|---|---|---|
| RW-001 | yawintutor.com | Circular dependency (constructor injection) | 2.3 | STARTUP | 2.7 | 2.7 | ✅ CORRECT |
| RW-002 | yawintutor.com | DataSource not configured | 3.x | STARTUP | 4.2 | 4.2 | ✅ CORRECT |
| RW-003 | yawintutor.com | No PasswordEncoder mapped for id "null" | 2.2 | RUNTIME | 6.5 | 6.5 | ✅ CORRECT |
| RW-004 | GitHub spring-boot#4519 | NoSuchBeanDefinitionException | 1.3 (legacy) | STARTUP | none | none | ✅ CORRECT (acknowledged gap) |
| RW-005 | GitHub shardingsphere#7933 | NoSuchBeanDefinitionException (partial log) | 2.3.1 | STARTUP | none | none | ✅ CORRECT (acknowledged gap) |
| SOL-001 | jvt.me | NoSuchBeanDefinitionException in @SpringBootTest | 2.x | TEST | 2.3 | 2.3 | ✅ CORRECT |
| SOL-002 | ggorantala.dev | NoSuchBeanDefinitionException with banner | unspec. | STARTUP | 2.1 | 2.1 | ✅ CORRECT |
| SOL-003 | GitHub spring-boot#34394 | UnsatisfiedDependency → Redis missing | 3.0.2 | STARTUP | 4.14 | 4.14 | ✅ CORRECT (gap closed in 0.1.3) |
| SOL-004 | GitHub spring-boot#19382 | PortInUseException in @SpringBootTest | 2.0–2.2 | TEST | 1.10 | 1.10 | ✅ CORRECT |
| SOL-005 | yawintutor.com | @Value placeholder not resolved | 2.2.4 | STARTUP | 3.1 | 3.1 | ✅ CORRECT |
| SOL-006 | GitHub spring-boot#15828 | DataSource not configured | 2.0.2 | STARTUP | 4.2 | 4.2 | ✅ CORRECT |
| SOL-007 | GitHub redisson#2798 | LazyInitializationException | 2.1.1 | RUNTIME | 4.8 | 4.8 | ✅ CORRECT |
| SOL-008 | ggorantala.dev | BeanCurrentlyInCreationException (SB 2.6+) | 2.6+ | STARTUP | 1.13 | 1.13 | ✅ CORRECT |
| SOL-009 | Medium/@junjaboy | Ambiguous handler mapping | 3.2.0 | STARTUP | 5.8 | 5.8 | ✅ CORRECT |
| SOL-010 | GitHub spring-boot#38617 | NoSuchMethodError in test context | 3.2.0 | TEST | 10.1 | 10.1 | ✅ CORRECT (improved in 0.1.3) |

**Match rate on cases that should match: 13/13 (100%)**  
**False positives: 0**  
**Acknowledged false negatives: 2** (RW-004 legacy 1.x format, RW-005 partial log — both format limitations, not classifier errors)

### What changed in 0.1.3

Three corpus-driven changes, each validated by the suite:

- **SOL-003 gap closed** — added rule 4.14 (RedisConnectionFactory not configured). Was a NO_MATCH, now matches 4.14.
- **SOL-010 improved** — rule 10.1 (NoSuchMethodError) now covers the TEST phase, and rule 1.10 was moved to the end of the catalog as a true last-resort catch-all. SOL-010 now gets the actionable "version conflict" diagnosis (10.1) instead of the generic catch-all (1.10).
- **SOL-001 improved (side effect of the reorder)** — with 1.10 no longer grabbing it first, the deepest Caused by (UnsatisfiedDependencyException) is now matched by rule 2.3, a more specific diagnosis than the catch-all. This was caught by the full-suite run, not predicted — a good argument for gating on the whole suite, not the real-world subset.

---

## Bugs Found and Fixed During Testing (v0.1.1)

### Bug 1 — Missing phase filter in classifier

**Found during:** RW-003 testing  
**Symptom:** Rule 6.4 (`buildLineContains: "WebSecurityConfigurerAdapter"`, `phases: [COMPILE]`) fired on a RUNTIME log because the stack frame contained `WebSecurityConfigurerAdapter$LazyPasswordEncoder`.  
**Fix:** Added a phase guard in `RuleBasedClassifier.classify()`: rules with a declared phase list are now skipped when the signal's phase is not in that list.  
**Impact:** All COMPILE-phase build rules are now correctly excluded from STARTUP and RUNTIME logs.

### Bug 2 — Rule 13.4 signal too broad

**Found during:** RW-005 testing (after removing source attribution from log files)  
**Symptom:** Rule 13.4 (MapStruct mapper not a Spring bean) matched a MyBatis-Plus DAO injection because `messageContains: "Mapper"` matched the field name `baseMapper`.  
**Fix:** Changed to `causedByMessage: "Mapper"` — only the deepest `Caused by:` exception message is checked. For MapStruct errors the missing bean type ends with "Mapper"; for other DAOs it does not.  
**Impact:** Eliminates false positives when a non-MapStruct injection point has "mapper" in its field name.

---

## Acknowledged Gaps (False Negatives)

### RW-004 — Spring Boot 1.x legacy log format

**Source:** GitHub spring-projects/spring-boot#4519, Spring Boot 1.3.0  
**Error:** UnsatisfiedDependencyException wrapping NoSuchBeanDefinitionException  
**Gap:** Pre-1.4 logs use a comma-separated timestamp format (`21:52:01,504`) and chain exceptions with `nested exception is` rather than Java `Caused by:` blocks. The failure analysis banner (Spring Boot 1.4+) is absent.  
**Assessment:** Accepted. Spring Boot 1.x is well past EOL.

### RW-005 — Partial log (missing failure analysis banner)

**Source:** GitHub apache/shardingsphere#7933, Spring Boot 2.3.1  
**Error:** NoSuchBeanDefinitionException for a MyBatis-Plus DAO  
**Gap:** The log excerpt contains a proper `Caused by: NoSuchBeanDefinitionException` line, but the deepest exception message says "expected at least 1 bean which qualifies" — not "required a bean of type" (which only appears in the failure analysis banner that was omitted from the pasted log).  
**Assessment:** In the full IntelliJ console the banner would be present and rule 2.1 would match. Partial logs pasted by users to GitHub are an inherent limitation.

> **SOL-003 (Redis) was a gap in 0.1.2 and is now closed** by rule 4.14. It is
> no longer a false negative — see the 0.1.3 changes above.

---

## Interesting Findings from Testing

### Rule 1.10 is the last-resort catch-all for test context failures

Many distinct error types produce "Failed to load ApplicationContext" in the test console. Rule 1.10 (`messageContains: "Failed to load ApplicationContext"`, `phases: [TEST]`) catches these and directs the developer to the nested exception.

In 0.1.3 it was moved to the **very end** of the catalog so that any more specific rule wins first. The effect is visible in the corpus: SOL-010 now matches 10.1 (version conflict) and SOL-001 now matches 2.3 (unsatisfied dependency) instead of both falling to 1.10. SOL-004 (PortInUseException, no dedicated rule) still falls to 1.10.

Practical implication: developers always see _something_ when a test context fails to load, but they get the most specific available diagnosis rather than the generic one.

### Phase filter prevents COMPILE rules from polluting RUNTIME diagnoses

Before the v0.1.1 fix, rule 6.4 (`WebSecurityConfigurerAdapter`, `phases: [COMPILE]`) would have fired on any RUNTIME security exception whose stack trace mentioned `WebSecurityConfigurerAdapter$LazyPasswordEncoder`. The diagnosis would have told the developer to migrate away from `WebSecurityConfigurerAdapter` — which is the wrong advice when the real problem is DelegatingPasswordEncoder needing encoded passwords. The phase filter is essential for diagnostic accuracy.

### Rule 1.13 vs 2.7 ordering (circular dependency)

SOL-008 confirms that rule 1.13 (BeanCurrentlyInCreationException via `Caused by:` line) correctly fires before rule 2.7 (cycle banner text) when both signals are present. This is the intended catalog ordering: exception-based signals in section 1 are more specific and win over message-based signals in section 2.

### NoSuchMethodError in test context — fixed in 0.1.3

In 0.1.2, rule 10.1 (`phases: [STARTUP, RUNTIME]`) was skipped for TEST-phase signals and SOL-010 fell to the generic 1.10 catch-all. In 0.1.3, rule 10.1 was extended to the TEST phase and 1.10 moved to the end of the catalog, so SOL-010 now surfaces the actionable "run mvn dependency:tree to find the version conflict" advice.

### Only validated rules fire (0.1.3)

The classifier now skips any rule whose status is not DONE. Previously a TODO rule (e.g. 13.8, LOW confidence) was technically active and only hidden by the default confidence gate. "DONE" now genuinely gates production behaviour, matching the testing contract.

---

## Rule Coverage Summary

| Section | Rules DONE | Real-world confirmed | Notes |
|---|---|---|---|
| 1 — Startup | 10 | 2 (1.10 via SOL-004, 1.13 via SOL-008) | 1.10 moved to end as last-resort catch-all |
| 2 — Dependency injection | 4 | 3 (2.1, 2.3, 2.7) | 2.3 now confirmed via SOL-001 |
| 3 — Configuration | 3 | 1 (3.1) | |
| 4 — JPA/Data | 7 | 4 (4.2 ×2, 4.8, 4.14) | Redis rule 4.14 added |
| 5 — Web/MVC | 3 | 1 (5.8) | |
| 6 — Security | 3 | 1 (6.5) | Phase filter fixed 6.4 |
| 7 — Jackson | 2 | 0 | No real-world logs found yet |
| 9 — Testing | 2 | 0 | 9.1 duplicate removed; 9.6 Testcontainers added |
| 10 — Build/packaging | 3 | 1 (10.1) | 10.1 now covers TEST phase |
| 13 — MapStruct | 5 of 6 | 0 | 13.8 stays TODO (needs PSI to verify null-mapping claim) |

---

## Confidence Calibration

All 13 MATCH cases fired at the expected confidence:

| Rule | Confidence | Confirmed in |
|---|---|---|
| 1.10 | HIGH | SOL-004 |
| 1.13 | HIGH | SOL-008 |
| 2.1 | HIGH | SOL-002 |
| 2.3 | MEDIUM | SOL-001 |
| 2.7 | HIGH | RW-001 |
| 3.1 | HIGH | SOL-005 |
| 4.2 | HIGH | RW-002, SOL-006 |
| 4.8 | HIGH | SOL-007 |
| 4.14 | HIGH | SOL-003 |
| 5.8 | HIGH | SOL-009 |
| 6.5 | HIGH | RW-003 |
| 10.1 | HIGH | SOL-010 |

No rule produced an incorrect diagnosis in any of the 15 real-world test cases.

---

## Planned for v0.2.0 and beyond

Done in 0.1.3: rule 10.1 TEST phase, Redis rule 4.14, rule 9.1 duplicate removed. Remaining:

- Expand real-world test corpus to 25+ cases
- Source logs for Jackson infinite recursion and DataIntegrityViolationException (sections 7 and 4.13 have no real-world confirmation yet)
- **M6 proper** — register the build tap via a non-deprecated `CompileTask` API (the one remaining correctness debt; build rules currently match via the rawExcerpt fallback)
- **M8 PSI enrichment** — unblocks rule 13.8 (MapStruct null-mapping), which stays TODO until its null-value-strategy claim can be verified rather than guessed; a LOW-confidence guess would mislabel the diagnosis
- **M9 Actuator enrichment**, **M13 Ollama LLM fallback**
