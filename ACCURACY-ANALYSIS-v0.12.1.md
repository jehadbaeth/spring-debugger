# Accuracy and Performance Analysis: v0.12.1

**Release theme:** one new rule, 4.18, for a database password authentication failure, reported from
a real bootRun log.
**Date:** 2026-06-26

## 1. The new rule

**4.18 — Database password authentication failed.** Signal: `messageContains` "password
authentication failed for user" (PostgreSQL SQLState 28P01, wrapped by Hibernate's
GenericJDBCException "unable to obtain isolated JDBC connection"). Phases STARTUP, RUNTIME, TEST.
Confidence HIGH.

- **Diagnosis:** the database is reachable but rejected the credentials; the username exists but the
  password is wrong or empty, usually an unset environment variable (for example POSTGRES_PASSWORD)
  that the datasource password references, so the placeholder resolved to nothing.
- **Fix:** set the POSTGRES_PASSWORD environment variable that spring.datasource.password references,
  or hardcode a value in application-local.properties, matching what the database expects for that
  user.

It is deliberately distinct from 4.15 (refused TCP connection): there the server is unreachable;
here it is reachable and rejects the password.

## 2. Why it does not collide with existing rules

The golden was regenerated and the diff added **only** the new fixture's entry; every other entry of
the 93-log corpus is byte-identical. That proves rule 4.18's signal ("password authentication failed
for user") matches the new fixture and nothing else, and that no earlier rule intercepts the new
fixture (it classifies to 4.18). The Java `ClassifierFixtureTest` confirms the fixture classifies to
itself.

## 3. Coverage and parity

| Engine | Tests | Notes |
|---|---|---|
| Java (IntelliJ) | 204 | +1 fixture test; catalog now 61 rules |
| TypeScript (VS Code) | 196 | +1 parity assertion for the new fixture |

The rule ships in both products from the single shared `spring-boot-rules.yaml`; the cross-engine
parity test confirms the Java and TypeScript engines produce the identical 4.18 card.

## 4. Tooling note

Fixed a real gap found while regenerating the golden: Gradle ran the test fork without forwarding
`-Dparity.regenerate`, so the documented regenerate flag silently did nothing once the golden
existed (it had only ever worked on first creation, via the file-absent path). `build.gradle` now
forwards the property into the test JVM.

## 5. Performance

No performance impact. One additional substring check in the rule loop.
