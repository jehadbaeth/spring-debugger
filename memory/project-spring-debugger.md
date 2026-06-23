---
name: project-spring-debugger
description: Core context for the Spring Boot Debugger IntelliJ plugin project
metadata:
  type: project
---

IntelliJ-first plugin (Java) that monitors run/test/build output and produces a one-to-two sentence diagnosis card for Spring Boot errors. Offline rule engine first; LLM (Ollama) behind a config switch, deferred.

**Why:** Developer wants a tool that eliminates manual stack-trace reading for common Spring Boot errors. Started with a comprehensive debugging guide (spring-boot-debugging-guide.md) covering 80+ error categories across startup, DI, config, JPA, web, security, Jackson, OpenAPI, testing, build, and runtime phases.

**Architecture decisions made (see PLAN.md):**
- Three separate IntelliJ taps: RUN_CONSOLE (ProcessListener), TEST_CONSOLE (SMTRunnerEventsListener), BUILD_OUTPUT (CompileContext). Not one unified log stream.
- PSI for enrichment (not LSP; LSP is for external servers, PSI is IntelliJ's native model).
- Rules stored in YAML (spring-boot-rules.yaml), loaded at runtime. Not hardcoded in Java.
- Output type: DiagnosisCard record with ruleId, phase, diagnosisSentence, fixSentence, confidence, excerpt.
- LLM adapter implements same interface as rule engine; LLM fires only on confidence == NONE.
- Ollama preferred for LLM mode (local, satisfies security constraints).
- IntelliJ Ultimate's Spring plugin bean model is an optional enrichment source (must not break Community build).

**Milestone order:** M0 plugin skeleton -> M1 RUN_CONSOLE tap -> M2 extractor -> M3 YAML rule loader -> M4 sections 1 and 2 with fixtures -> M5 TEST_CONSOLE -> M6 BUILD_OUTPUT -> M7 full catalog -> M8 PSI enrichment -> M9 Actuator enrichment -> M10 UI polish -> M11 LLM.

**How to apply:** When implementing, always check PLAN.md for rule signals and templates. Never hardcode rules in Java. A rule is not DONE until a fixture log file exists and CI passes against it.

**CI:** User is setting up a remote GitHub repo with CI. The CI test contract is defined in PLAN.md section 6: every rule must have a fixture, classifier must return the correct ruleId and confidence >= MEDIUM.
