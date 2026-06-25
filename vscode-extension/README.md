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

## Settings

All under `springDebugger.*`: `enabled`, `minimumConfidence`, `maxHistory`, `showNotifications`,
`watchTestResults`, `watchLogFile`, `logFilePath`.

## Privacy

Fully offline. No network calls. History and settings stay in your workspace.
