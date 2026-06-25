# Spring Boot Debugger (VS Code)

Watches your Spring Boot project's run, test, and build output and, when it recognises an error,
shows a short card: one sentence naming the problem, one giving the fix. No stack-trace reading
required.

This is the VS Code sibling of the IntelliJ plugin. Both share the same rule catalog
(`spring-boot-rules.yaml`) and are kept in lockstep by a cross-engine parity test, so a rule added
for one is a rule for both.

## How it captures output

VS Code extensions cannot read the integrated terminal, so the extension captures the output your
project produces on disk instead:

- **Test results** — watches `build/test-results` (Gradle) and `surefire-reports` (Maven). Running
  `./gradlew test` in any terminal is enough; no configuration.
- **Log file tailing** — auto-discovers every `logging.file.name` declared in your
  `application*.properties` / `*.yml` (one per service in a multi-module build) and tails it as
  `bootRun` appends to it. Only the most recent run is diagnosed, so stale errors from earlier runs
  are never re-surfaced.
- **Diagnose Pasted Output** — the command `Spring Boot Debugger: Diagnose Pasted Output` diagnoses
  whatever is on your clipboard (or what you type), as a guaranteed fallback.

Console-only apps need a committed `logging.file.name` for the tailing path. Spring Boot 3.x uses
`logging.file.name`, not the removed `logging.file`.

## Sharper diagnoses (enrichment) and an optional LLM fallback

- **Source enrichment** (`springDebugger.enrichSource`, on by default) inspects your Java source via
  the workspace symbol provider to name the exact missing type, where it lives, and which annotation
  fits, upgrading confidence. It is best with the Java extension (`redhat.java`) installed; without
  it, this quietly no-ops and rules still fire.
- **Actuator enrichment** (`springDebugger.actuator.enabled`, off by default) confirms a runtime
  diagnosis against a running app's `/actuator/health` and `/actuator/env`. Off by default because it
  makes HTTP calls to your app (`springDebugger.actuator.baseUrl`).
- **LLM fallback** (`springDebugger.ollama.enabled`, off by default) asks a local Ollama model for a
  best-effort diagnosis when no rule matches. Local only, never a cloud provider.

## Settings

All under `springDebugger.*`: `enabled`, `minimumConfidence`, `maxHistory`, `showNotifications`,
`watchTestResults`, `watchLogFile`, `logFilePath`, `enrichSource`, `actuator.enabled`,
`actuator.baseUrl`, `ollama.enabled`, `ollama.baseUrl`, `ollama.model`.

## Privacy

Offline by default: with the default settings there are no network calls. The only outbound traffic
is opt-in and local, the Actuator probe to your own app and the Ollama fallback to localhost. History
and settings stay in your workspace.
