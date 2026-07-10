# Accuracy and Performance Analysis: v0.14.0

**Release theme:** eight new Spring Boot layering convention checks in the IntelliJ code convention
engine, driven by a new team source document. The log diagnoser and the Robot/Javadoc rules from
v0.13.0 are unchanged.
**Date:** 2026-07-10

## 1. What is new

Eight `checkType` implementations, added to the same `ConventionCatalog` / `CheckRegistry` /
`ConventionInspection` engine shipped in v0.13.0, driven by `docs/spring-boot-conventions.md` (the
GMS Service Modules Spring Boot conventions). Nine rule entries total (`optionalUsage` is reused by
two rules with different `target` params).

All nine default to `WEAK_WARNING` and are phrased as suggestions ("consider..."), not mandates —
these are team preferences, not hard errors, and each still turns off individually in Settings the
same way every other rule does.

## 2. Rules shipped

| Rule | checkType | Enforces |
|---|---|---|
| FIELD_INJECTION_FORBIDDEN | fieldInjectionForbidden | Fields are not injected via `@Autowired`/`@Inject`; prefer constructor injection |
| NO_SYSTEM_OUT_ERR | noSystemOutErr | No `System.out`/`System.err`; prefer a logger |
| OPTIONAL_AS_PARAMETER | optionalUsage (target: parameter) | `Optional` is not used as a method parameter type |
| OPTIONAL_AS_FIELD | optionalUsage (target: field) | `Optional` is not stored in a field |
| TRANSACTIONAL_MISPLACED | transactionalMisplaced | `@Transactional` is not on a `@RestController` or `@Repository` class/method |
| ENTITY_STRING_FIELD_BOUNDED | entityStringFieldBounded | `String` fields on `@Entity` classes carry a bound (`@Size`, `@Column(length=...)`, or `@Convert`) |
| REQUEST_BODY_REQUIRES_VALID | requestBodyRequiresValid | `@RequestBody` parameters are paired with `@Valid`/`@Validated` |
| SERVICE_CLASS_NAMING | serviceClassNaming | `@Service` classes/interfaces are named with a `Service` suffix |
| API_VERSION_PATH | apiVersionPath | Controller base paths match `/api/v{N}/...` (literal-value paths only) |

All nine are single-file Java PSI checks: no IO, no cross-file resolution, same purity contract as
the existing checks.

## 3. Accuracy notes

- **Scope of the source document.** Most of `docs/spring-boot-conventions.md` is package-layout
  architecture (what belongs in `service/` vs `repository/` vs `rest_api/`) and cross-file rules
  (entities never returned from services, controllers only call services, DTOs never reach the
  service layer) that need symbol resolution across files. That is out of scope for the same reason
  v0.13.0's plan deferred cross-file checks: no parity story for non-deterministic IDE symbol
  providers. Semantic conventions (`readOnly = true` for reads, `Instant` over `LocalDateTime`,
  `Optional` unwrapped with `orElseThrow`) were left out too — they need intent, not syntax, and
  would be false-positive magnets. Only the mechanically checkable subset shipped; the rest is
  documentation, not enforcement.
- **Annotation matching by simple name, not qualified name.** `LightJavaCodeInsightFixtureTestCase`
  fixtures have no Spring/Jakarta classpath, so a qualified-name match would silently no-op in tests
  while working in a real project (or vice versa). All eight checks match `@Autowired`,
  `@Transactional`, `@Entity`, `@RequestBody`, `@Service`, etc. by simple name, the same pattern
  `JavadocRequiredCheck` already uses for `@Override`. This is a known, accepted tradeoff: a
  same-named annotation from an unrelated package would also match. No such collision exists in the
  Spring/Jakarta ecosystem for the names used here.
- **`apiVersionPath` false-positive guard.** Only fires when the mapping annotation's `value`/`path`
  attribute is a literal string. A constant reference (`Sample.PATH`) or any other non-literal
  expression is silently skipped rather than guessed at, so the rule cannot misfire on paths it
  cannot read.
- **Params are the team-tuning surface**, same as the Robot Test ID scope in v0.13.0: the injection
  annotation list, the entity-bounding annotation set, the `@Service` suffix, and the version path
  pattern are all adjustable in `conventions.yaml` without a code change.

## 4. Coverage

| Engine | Tests | Notes |
|---|---|---|
| Java (IntelliJ) | 253 | +20 for the eight new Spring Boot checks (two positive/negative fixtures each, three for the entity check, three for the version-path check) |
| TypeScript (VS Code) | 196 | Unchanged; these rules are IntelliJ only this release |

## 5. Known limitation (honest)

Simple-name annotation matching (see section 3) means a project-local annotation named e.g.
`@Service` that is not Spring's `@org.springframework.stereotype.Service` would also trigger
`SERVICE_CLASS_NAMING`. Considered acceptable: these are `WEAK_WARNING` suggestions, not blocking
errors, and the annotation names are visible in `conventions.yaml` if a team needs to narrow them
further (e.g. to a fully-qualified check) in a later revision.

## 6. Performance

No impact on the log diagnoser or the Robot/Javadoc rules. Each new check is a bounded single-file
PSI traversal, run under the same `ConventionInspection` umbrella and the same per-rule
enabled/disabled gating as every other convention rule.
