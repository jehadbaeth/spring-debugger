package com.springdebugger.settings;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.springdebugger.model.Confidence;

import javax.swing.*;
import java.awt.*;

/**
 * Settings panel shown inside the IDE Preferences dialog under Tools > Spring Boot Debugger.
 */
public final class SpringDebuggerSettingsPanel {

    private JPanel root;
    private JBCheckBox enabledBox;
    private JComboBox<String> confidenceCombo;
    private JSpinner maxHistorySpinner;
    private JBCheckBox showBalloonBox;
    private JBCheckBox focusToolWindowBox;
    private JBCheckBox llmEnabledBox;
    private JBTextField ollamaUrlField;
    private JBTextField ollamaModelField;
    private JBCheckBox watchTestResultsBox;
    private JBCheckBox watchLogFileBox;
    private JBTextField logFilePathField;
    private JBCheckBox experimentalTerminalBox;

    public SpringDebuggerSettingsPanel() {
        build();
        reset();
    }

    private void build() {
        root = new JPanel(new GridBagLayout());
        root.setBorder(JBUI.Borders.empty(8));

        GridBagConstraints label = labelConstraints();
        GridBagConstraints field = fieldConstraints();

        int row = 0;

        // ── General section ─────────────────────────────────────────────────
        addSectionHeader(root, "General", row++);

        enabledBox = new JBCheckBox("Enable Spring Boot Debugger");
        addRow(root, enabledBox, row++);

        showBalloonBox = new JBCheckBox("Show notification balloon on new diagnosis");
        addRow(root, showBalloonBox, row++);

        focusToolWindowBox = new JBCheckBox("Focus tool window when an error is detected");
        addRow(root, focusToolWindowBox, row++);

        // ── Terminal capture section ─────────────────────────────────────────
        addSectionHeader(root, "Terminal capture (for runs started in a terminal)", row++);

        watchTestResultsBox = new JBCheckBox("Watch test result files (build/test-results, surefire-reports)");
        addRow(root, watchTestResultsBox, row++);

        watchLogFileBox = new JBCheckBox("Tail the application log file for bootRun");
        addRow(root, watchLogFileBox, row++);

        logFilePathField = new JBTextField();
        addLabeledComponent(root, "Log file path (blank = auto-detect logging.file.name):", logFilePathField, row++);

        experimentalTerminalBox = new JBCheckBox("Experimental: monitor the new (Gen2) terminal directly");
        addRow(root, experimentalTerminalBox, row++);

        JBLabel terminalNote = new JBLabel(
            "<html><i>Test-result watching needs no setup. Log tailing needs a log file (set<br>" +
            "logging.file.name) since console-only output has nothing to read. The new-terminal<br>" +
            "option is best-effort and unverified across IDE versions; if in doubt, use<br>" +
            "Diagnose pasted output, which always works.</i></html>");
        terminalNote.setForeground(UIManager.getColor("Label.disabledForeground"));
        addRow(root, terminalNote, row++);

        watchLogFileBox.addActionListener(e -> updateLogFieldState());

        // ── Analysis section ─────────────────────────────────────────────────
        addSectionHeader(root, "Analysis", row++);

        addLabeledComponent(root, "Minimum confidence level:", createConfidenceCombo(), row++);
        addLabeledComponent(root, "Maximum history entries:", createMaxHistorySpinner(), row++);

        // ── LLM section ──────────────────────────────────────────────────────
        addSectionHeader(root, "LLM Fallback (local Ollama)", row++);

        llmEnabledBox = new JBCheckBox("Enable Ollama LLM fallback for unrecognised errors");
        addRow(root, llmEnabledBox, row++);

        ollamaUrlField = new JBTextField("http://localhost:11434");
        addLabeledComponent(root, "Ollama base URL:", ollamaUrlField, row++);

        ollamaModelField = new JBTextField("llama3.2");
        addLabeledComponent(root, "Ollama model:", ollamaModelField, row++);

        JBLabel llmNote = new JBLabel(
            "<html><i>The fallback runs only when no rule matches. All data stays on your<br>" +
            "machine: requests go to your local Ollama instance, never to a cloud provider.</i></html>");
        llmNote.setForeground(UIManager.getColor("Label.disabledForeground"));
        addRow(root, llmNote, row++);

        // URL and model are only meaningful when the fallback is enabled.
        llmEnabledBox.addActionListener(e -> updateLlmFieldState());
        updateLlmFieldState();

        // filler
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0; filler.gridy = row; filler.weighty = 1.0;
        filler.gridwidth = 2; filler.fill = GridBagConstraints.VERTICAL;
        root.add(new JPanel(), filler);
    }

    private JComboBox<String> createConfidenceCombo() {
        confidenceCombo = new JComboBox<>(new String[]{"HIGH", "MEDIUM", "LOW"});
        return confidenceCombo;
    }

    private JSpinner createMaxHistorySpinner() {
        maxHistorySpinner = new JSpinner(new SpinnerNumberModel(30, 5, 200, 5));
        maxHistorySpinner.setPreferredSize(new Dimension(70, maxHistorySpinner.getPreferredSize().height));
        return maxHistorySpinner;
    }

    private void addSectionHeader(JPanel panel, String title, int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = row; c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.insets = row == 0 ? JBUI.insets(0, 0, 4, 0) : JBUI.insets(12, 0, 4, 0);
        JBLabel header = new JBLabel("<html><b>" + title + "</b></html>");
        panel.add(header, c);
    }

    private void addRow(JPanel panel, JComponent component, int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = row; c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.insets = JBUI.insets(2, 8, 2, 0);
        panel.add(component, c);
    }

    private void addLabeledComponent(JPanel panel, String labelText, JComponent component, int row) {
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = row; lc.anchor = GridBagConstraints.WEST;
        lc.insets = JBUI.insets(2, 8, 2, 8);
        panel.add(new JBLabel(labelText), lc);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1; fc.gridy = row; fc.anchor = GridBagConstraints.WEST;
        fc.insets = JBUI.insets(2, 0, 2, 0);
        fc.fill = GridBagConstraints.HORIZONTAL; fc.weightx = 1.0;
        panel.add(component, fc);
    }

    private GridBagConstraints labelConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST; c.insets = JBUI.insets(2, 8, 2, 8);
        return c;
    }

    private GridBagConstraints fieldConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        c.anchor = GridBagConstraints.WEST; c.insets = JBUI.insets(2, 0, 2, 0);
        return c;
    }

    public JPanel getComponent() { return root; }

    public boolean isModified() {
        SpringDebuggerSettings s = SpringDebuggerSettings.getInstance();
        return enabledBox.isSelected() != s.isEnabled()
            || showBalloonBox.isSelected() != s.isShowNotificationBalloon()
            || focusToolWindowBox.isSelected() != s.isFocusToolWindowOnError()
            || !confidenceCombo.getSelectedItem().toString().equals(s.getMinimumConfidence().name())
            || (int) maxHistorySpinner.getValue() != s.getMaxHistorySize()
            || llmEnabledBox.isSelected() != s.isLlmEnabled()
            || !ollamaUrlField.getText().equals(s.getOllamaBaseUrl())
            || !ollamaModelField.getText().equals(s.getOllamaModel())
            || watchTestResultsBox.isSelected() != s.isWatchTestResults()
            || watchLogFileBox.isSelected() != s.isWatchLogFile()
            || !logFilePathField.getText().equals(s.getLogFilePath())
            || experimentalTerminalBox.isSelected() != s.isExperimentalNewTerminal();
    }

    public void apply() {
        SpringDebuggerSettings s = SpringDebuggerSettings.getInstance();
        s.setEnabled(enabledBox.isSelected());
        s.setShowNotificationBalloon(showBalloonBox.isSelected());
        s.setFocusToolWindowOnError(focusToolWindowBox.isSelected());
        s.setMinimumConfidence(Confidence.valueOf(confidenceCombo.getSelectedItem().toString()));
        s.setMaxHistorySize((int) maxHistorySpinner.getValue());
        s.setLlmEnabled(llmEnabledBox.isSelected());
        s.setOllamaBaseUrl(ollamaUrlField.getText().trim());
        s.setOllamaModel(ollamaModelField.getText().trim());
        s.setWatchTestResults(watchTestResultsBox.isSelected());
        s.setWatchLogFile(watchLogFileBox.isSelected());
        s.setLogFilePath(logFilePathField.getText().trim());
        s.setExperimentalNewTerminal(experimentalTerminalBox.isSelected());
    }

    public void reset() {
        SpringDebuggerSettings s = SpringDebuggerSettings.getInstance();
        enabledBox.setSelected(s.isEnabled());
        showBalloonBox.setSelected(s.isShowNotificationBalloon());
        focusToolWindowBox.setSelected(s.isFocusToolWindowOnError());
        confidenceCombo.setSelectedItem(s.getMinimumConfidence().name());
        maxHistorySpinner.setValue(s.getMaxHistorySize());
        llmEnabledBox.setSelected(s.isLlmEnabled());
        ollamaUrlField.setText(s.getOllamaBaseUrl());
        ollamaModelField.setText(s.getOllamaModel());
        watchTestResultsBox.setSelected(s.isWatchTestResults());
        watchLogFileBox.setSelected(s.isWatchLogFile());
        logFilePathField.setText(s.getLogFilePath());
        experimentalTerminalBox.setSelected(s.isExperimentalNewTerminal());
        updateLlmFieldState();
        updateLogFieldState();
    }

    private void updateLlmFieldState() {
        boolean on = llmEnabledBox.isSelected();
        ollamaUrlField.setEnabled(on);
        ollamaModelField.setEnabled(on);
    }

    private void updateLogFieldState() {
        logFilePathField.setEnabled(watchLogFileBox.isSelected());
    }
}
