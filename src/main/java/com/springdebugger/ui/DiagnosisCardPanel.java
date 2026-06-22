package com.springdebugger.ui;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.service.DiagnosisHistoryService;
import com.springdebugger.settings.SpringDebuggerSettings;

import javax.swing.*;

/**
 * Entry point for surfacing a DiagnosisCard to the user.
 *
 * 1. Always pushes the card into DiagnosisHistoryService (which notifies the tool window).
 * 2. Optionally shows a notification balloon (controlled by settings).
 * 3. Optionally activates the Spring Debugger tool window (controlled by settings).
 */
public final class DiagnosisCardPanel {

    private static final String NOTIFICATION_GROUP = "Spring Debugger";
    private static final String TOOL_WINDOW_ID    = "Spring Debugger";

    private DiagnosisCardPanel() {}

    public static void show(Project project, DiagnosisCard card) {
        DiagnosisHistoryService.getInstance(project).addDiagnosis(card);

        SpringDebuggerSettings settings = SpringDebuggerSettings.getInstance();

        if (settings.isShowNotificationBalloon()) {
            String content = "<b>" + escapeHtml(card.getDiagnosisSentence()) + "</b><br>"
                + escapeHtml(card.getFixSentence());
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("Spring Boot Error Detected", content, NotificationType.WARNING)
                .notify(project);
        }

        if (settings.isFocusToolWindowOnError()) {
            ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
            if (tw != null) tw.activate(null);
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
