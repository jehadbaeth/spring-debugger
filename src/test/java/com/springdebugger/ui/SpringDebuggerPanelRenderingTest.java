package com.springdebugger.ui;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.service.DiagnosisHistoryService;

import javax.swing.JTextArea;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the literal Swing rendering of a diagnosis card: the real tool-window panel is
 * constructed, a card is pushed through the history service, and after the EDT settles the
 * card's diagnosis and fix text actually appear in real JTextArea components in the panel's
 * Swing tree. This is the headless stand-in for "the card renders in the tool window".
 */
public class SpringDebuggerPanelRenderingTest extends BasePlatformTestCase {

    public void testCardTextRendersIntoSwingComponents() {
        DiagnosisHistoryService history = DiagnosisHistoryService.getInstance(getProject());
        history.clear();

        // Build the real panel (registers its history listener and does an initial refresh).
        SpringDebuggerPanel panel = new SpringDebuggerPanel(getProject());

        String diagnosis = "Spring cannot find a bean of the required type in this rendering test.";
        String fix = "Annotate the class with @Service so component scanning registers it.";
        history.addDiagnosis(new DiagnosisCard("2.1", Phase.STARTUP, diagnosis, fix,
                Confidence.HIGH, "excerpt"));

        // The panel refreshes via invokeLater; pump the queue so the Swing tree updates.
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        List<String> textAreaContents = new ArrayList<>();
        collectTextAreas(panel, textAreaContents);

        assertTrue("the diagnosis text should be rendered in a JTextArea, was: " + textAreaContents,
                textAreaContents.contains(diagnosis));
        assertTrue("the fix text should be rendered in a JTextArea, was: " + textAreaContents,
                textAreaContents.contains(fix));
    }

    private void collectTextAreas(Component component, List<String> out) {
        if (component instanceof JTextArea area) {
            out.add(area.getText());
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectTextAreas(child, out);
            }
        }
    }
}
