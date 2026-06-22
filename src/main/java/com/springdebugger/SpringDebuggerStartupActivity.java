package com.springdebugger;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.springdebugger.rule.RuleCatalog;
import com.springdebugger.tap.RunConsoleTap;
import com.springdebugger.tap.TestConsoleTap;
import org.jetbrains.annotations.NotNull;

/**
 * Runs once per project on open. Wires all three taps to their respective event sources:
 * - RunConsoleTap to each new process handler (run configurations)
 * - TestConsoleTap to the test runner message bus
 * - BuildOutputTap to the compiler manager
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

        // BuildOutputTap is registered via plugin.xml compiler.afterTask extension (M6).
    }
}
