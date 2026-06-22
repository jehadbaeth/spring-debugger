package com.springdebugger.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.springdebugger.SpringDebuggerService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Creates the Spring Debugger tool window panel shown at the bottom of the IDE.
 */
public final class SpringDebuggerToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = buildPlaceholderPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    private JPanel buildPlaceholderPanel(Project project) {
        JPanel panel = new JPanel(new BorderLayout());

        int ruleCount = SpringDebuggerService.getInstance().getCatalog().size();
        String statusText = "<html><center>"
                + "<br><br>"
                + "<b>Spring Boot Debugger</b><br><br>"
                + "Monitoring run, test, and build output.<br>"
                + ruleCount + " rules loaded.<br><br>"
                + "Diagnosis cards will appear here when an error is detected."
                + "</center></html>";

        JLabel label = new JLabel(statusText, SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);

        return panel;
    }
}
