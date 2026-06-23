package com.springdebugger.tap;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.service.DiagnosisHistoryService;
import com.springdebugger.settings.SpringDebuggerSettings;

/**
 * End-to-end verification of the delegated-build path: an ExternalBuildOutputTap is registered
 * with the platform's external-system notification manager, then build output is dispatched
 * THROUGH the manager (exactly as the IDE does when Gradle/Maven run), and the resulting
 * diagnosis must reach the history model. This covers the event-delivery wiring that unit
 * tests of the tap could not — the only remaining uncovered piece is Gradle itself producing
 * the text, which is the build tool's job, not the plugin's.
 */
public class ExternalBuildTapDispatchIntegrationTest extends BasePlatformTestCase {

    private static final ProjectSystemId GRADLE = new ProjectSystemId("GRADLE");

    public void testDispatchedBuildOutputProducesCard() {
        SpringDebuggerSettings settings = SpringDebuggerSettings.getInstance();
        settings.setShowNotificationBalloon(false);
        settings.setFocusToolWindowOnError(false);

        DiagnosisHistoryService history = DiagnosisHistoryService.getInstance(getProject());
        history.clear();

        ExternalSystemProgressNotificationManagerImpl manager =
                (ExternalSystemProgressNotificationManagerImpl) ExternalSystemProgressNotificationManager.getInstance();
        ExternalBuildOutputTap tap = new ExternalBuildOutputTap();
        manager.addNotificationListener(tap);
        try {
            ExternalSystemTaskId id = ExternalSystemTaskId.create(
                    GRADLE, ExternalSystemTaskType.EXECUTE_TASK, getProject());

            // A Spring-specific compile error (rule 6.4), as a delegated Gradle build would emit it.
            manager.onTaskOutput(id,
                    "> Task :compileJava FAILED\n"
                    + "error: cannot find symbol WebSecurityConfigurerAdapter\n"
                    + "  class SecurityConfig extends WebSecurityConfigurerAdapter\n", true);
            manager.onEnd(id);

            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

            DiagnosisCard recent = history.getMostRecent();
            assertNotNull("dispatched build output should yield a diagnosis card", recent);
            assertEquals("6.4", recent.getRuleId());
        } finally {
            manager.removeNotificationListener(tap);
        }
    }

    public void testCleanBuildProducesNoCard() {
        DiagnosisHistoryService history = DiagnosisHistoryService.getInstance(getProject());
        history.clear();

        ExternalSystemProgressNotificationManagerImpl manager =
                (ExternalSystemProgressNotificationManagerImpl) ExternalSystemProgressNotificationManager.getInstance();
        ExternalBuildOutputTap tap = new ExternalBuildOutputTap();
        manager.addNotificationListener(tap);
        try {
            ExternalSystemTaskId id = ExternalSystemTaskId.create(
                    GRADLE, ExternalSystemTaskType.EXECUTE_TASK, getProject());
            manager.onTaskOutput(id, "> Task :compileJava\nBUILD SUCCESSFUL in 2s\n", true);
            manager.onEnd(id);

            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

            assertNull("a clean build must not produce a card", history.getMostRecent());
        } finally {
            manager.removeNotificationListener(tap);
        }
    }
}
