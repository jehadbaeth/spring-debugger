package com.springdebugger.ui;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.springdebugger.model.DiagnosisCard;

import javax.swing.*;
import java.awt.*;

/**
 * Renders a DiagnosisCard to the user.
 * For M0/M1: shows a sticky notification balloon in the IDE.
 * A dedicated tool window panel will replace this in M10.
 */
public final class DiagnosisCardPanel extends JPanel {

    private static final String NOTIFICATION_GROUP = "Spring Debugger";

    private DiagnosisCardPanel(DiagnosisCard card) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel ruleLabel = new JLabel("[" + card.getRuleId() + "] " + card.getPhase().name());
        ruleLabel.setFont(ruleLabel.getFont().deriveFont(Font.BOLD));

        JTextArea diagnosisArea = new JTextArea(card.getDiagnosisSentence());
        diagnosisArea.setLineWrap(true);
        diagnosisArea.setWrapStyleWord(true);
        diagnosisArea.setEditable(false);
        diagnosisArea.setOpaque(false);

        JTextArea fixArea = new JTextArea(card.getFixSentence());
        fixArea.setLineWrap(true);
        fixArea.setWrapStyleWord(true);
        fixArea.setEditable(false);
        fixArea.setOpaque(false);
        fixArea.setForeground(new Color(0, 100, 0));

        JPanel body = new JPanel(new GridLayout(2, 1, 0, 4));
        body.add(diagnosisArea);
        body.add(fixArea);

        add(ruleLabel, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
    }

    /** Shows a diagnosis card as an IDE notification balloon. */
    public static void show(Project project, DiagnosisCard card) {
        String content = "<b>" + escapeHtml(card.getDiagnosisSentence()) + "</b><br>"
                + escapeHtml(card.getFixSentence());

        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("Spring Boot Error Detected", content, NotificationType.WARNING)
                .notify(project);
    }

    public static JComponent createPanel(DiagnosisCard card) {
        return new DiagnosisCardPanel(card);
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
