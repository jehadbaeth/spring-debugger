package com.springdebugger;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.springdebugger.rule.RuleCatalog;
import com.springdebugger.settings.SpringDebuggerSettings;
import com.springdebugger.tap.RunConsoleTap;
import com.springdebugger.tap.TestConsoleTap;
import org.jetbrains.annotations.NotNull;

/**
 * Runs once per project on open. Wires the runtime taps to their event sources:
 * - RunConsoleTap to each new process handler (run configurations)
 * - TestConsoleTap to the test runner message bus
 *
 * The build taps (BuildOutputTap for internal JPS builds, ExternalBuildOutputTap for
 * delegated Gradle/Maven builds) are registered declaratively in plugin.xml rather than here.
 */
public final class SpringDebuggerStartupActivity implements StartupActivity.DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        RuleCatalog catalog = SpringDebuggerService.getInstance().getCatalog();

        project.getMessageBus()
                .connect()
                .subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
                    @Override
                    public void processStarted(
                            @NotNull String executorId,
                            @NotNull ExecutionEnvironment env,
                            @NotNull ProcessHandler handler) {
                        handler.addProcessListener(new RunConsoleTap(project, catalog));
                    }
                });

        project.getMessageBus()
                .connect()
                .subscribe(SMTRunnerEventsListener.TEST_STATUS, new TestConsoleTap(project, catalog));

        // Terminal-agnostic capture: watch JUnit result files so `./gradlew test` (or `mvn test`)
        // run in any terminal, classic or new, is diagnosed without hooking the terminal at all.
        if (SpringDebuggerSettings.getInstance().isWatchTestResults()) {
            com.springdebugger.service.TestResultsWatchService.getInstance(project).start();
        }
    }
}
