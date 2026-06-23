package com.springdebugger.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.springdebugger.SpringDebuggerService;
import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.service.DiagnosisHistoryService;
import com.springdebugger.settings.SpringDebuggerSettings;
import com.springdebugger.settings.SpringDebuggerSettingsConfigurable;
import com.springdebugger.terminal.TerminalMonitorService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.TerminalView;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Main content panel for the Spring Debugger tool window.
 *
 * Layout (top-to-bottom):
 *   StatusBar   — monitoring indicator, rule count, settings gear
 *   CardView    — most-recent diagnosis with action buttons
 *   HistoryList — older entries in a scrollable JBList
 */
public final class SpringDebuggerPanel extends SimpleToolWindowPanel {

    private final Project project;
    private final JBLabel statusLabel;
    private final CurrentCardView currentCardView;
    private final DefaultListModel<DiagnosisCard> historyModel;
    private final JBList<DiagnosisCard> historyList;
    private final JBLabel historyCountLabel;
    private final Runnable historyListener;

    public SpringDebuggerPanel(@NotNull Project project) {
        super(false, true);
        this.project = project;

        statusLabel = new JBLabel();
        currentCardView = new CurrentCardView();
        historyModel = new DefaultListModel<>();
        historyList = buildHistoryList();
        historyCountLabel = new JBLabel();

        setContent(buildContent());
        setToolbar(buildToolbar());

        historyListener = this::refresh;
        DiagnosisHistoryService.getInstance(project).addListener(historyListener);
        refresh();
    }

    // ── Toolbar ──────────────────────────────────────────────────────────────

    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(JBUI.Borders.empty(4, 6));

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        bar.add(statusLabel, BorderLayout.WEST);

        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        east.setOpaque(false);

        JButton terminalBtn = new JButton(AllIcons.Actions.Execute);
        terminalBtn.setToolTipText("Monitor an open terminal for Spring Boot errors");
        terminalBtn.setBorderPainted(false);
        terminalBtn.setContentAreaFilled(false);
        terminalBtn.setFocusPainted(false);
        terminalBtn.addActionListener(e -> chooseTerminal(terminalBtn));
        east.add(terminalBtn);

        JButton settingsBtn = new JButton(AllIcons.General.Settings);
        settingsBtn.setToolTipText("Open Spring Boot Debugger settings");
        settingsBtn.setBorderPainted(false);
        settingsBtn.setContentAreaFilled(false);
        settingsBtn.setFocusPainted(false);
        settingsBtn.addActionListener(e ->
            ShowSettingsUtil.getInstance().showSettingsDialog(project, SpringDebuggerSettingsConfigurable.class));
        east.add(settingsBtn);

        bar.add(east, BorderLayout.EAST);
        return bar;
    }

    /**
     * Lets the user pick which open terminal tab to monitor (or stop monitoring). A terminal
     * has no process handle, so the monitor polls the chosen tab's text buffer.
     */
    private void chooseTerminal(java.awt.Component anchor) {
        TerminalMonitorService monitor = TerminalMonitorService.getInstance(project);
        List<JBTerminalWidget> widgets = new java.util.ArrayList<>(TerminalView.getInstance(project).getWidgets());

        List<String> labels = new java.util.ArrayList<>();
        if (monitor.isMonitoring()) labels.add("■  Stop monitoring");
        for (JBTerminalWidget w : widgets) labels.add("▶  " + terminalLabel(w));

        if (labels.isEmpty()) {
            JBPopupFactory.getInstance().createMessage("No open terminals to monitor").showUnderneathOf(anchor);
            return;
        }

        JBPopupFactory.getInstance().createPopupChooserBuilder(labels)
            .setTitle("Monitor Terminal")
            .setItemChosenCallback(choice -> {
                if (choice.startsWith("■")) {
                    monitor.stop();
                    return;
                }
                int index = labels.indexOf(choice) - (monitor.isMonitoring() ? 1 : 0);
                if (index >= 0 && index < widgets.size()) {
                    monitor.monitor(widgets.get(index));
                }
            })
            .createPopup()
            .showUnderneathOf(anchor);
    }

    private String terminalLabel(JBTerminalWidget widget) {
        try {
            String title = widget.getTerminalTitle().getDefaultTitle();
            if (title != null && !title.isBlank()) return title;
        } catch (Throwable ignored) {
            // fall through to a generic label
        }
        return "Terminal";
    }

    // ── Content ───────────────────────────────────────────────────────────────

    private JComponent buildContent() {
        OnePixelSplitter splitter = new OnePixelSplitter(true, 0.45f);
        splitter.setFirstComponent(currentCardView);
        splitter.setSecondComponent(buildHistoryPanel());
        return splitter;
    }

    private JComponent buildHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // header row
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(JBUI.Borders.empty(4, 6));
        historyCountLabel.setForeground(UIUtil.getContextHelpForeground());
        header.add(historyCountLabel, BorderLayout.WEST);

        JButton clearBtn = new JButton("Clear");
        clearBtn.setFont(clearBtn.getFont().deriveFont(11f));
        clearBtn.addActionListener(e -> {
            DiagnosisHistoryService.getInstance(project).clear();
        });
        header.add(clearBtn, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        panel.add(ScrollPaneFactory.createScrollPane(historyList), BorderLayout.CENTER);
        return panel;
    }

    private JBList<DiagnosisCard> buildHistoryList() {
        JBList<DiagnosisCard> list = new JBList<>(historyModel);
        list.setEmptyText("No diagnosis history yet");
        list.setCellRenderer(new HistoryCellRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = list.locationToIndex(e.getPoint());
                    if (idx >= 0) currentCardView.show(historyModel.get(idx));
                }
            }
        });
        return list;
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    private void refresh() {
        if (!ApplicationManager.getApplication().isDispatchThread()) {
            ApplicationManager.getApplication().invokeLater(this::refresh);
            return;
        }

        SpringDebuggerSettings settings = SpringDebuggerSettings.getInstance();
        int ruleCount = SpringDebuggerService.getInstance().getCatalog().activeCount();

        if (settings.isEnabled()) {
            statusLabel.setText("● Monitoring  ·  " + ruleCount + " rules");
            statusLabel.setForeground(new JBColor(new Color(0, 140, 0), new Color(98, 195, 98)));
        } else {
            statusLabel.setText("○ Disabled");
            statusLabel.setForeground(UIUtil.getContextHelpForeground());
        }

        List<DiagnosisCard> cards = DiagnosisHistoryService.getInstance(project).getHistory();

        historyModel.clear();
        for (DiagnosisCard card : cards) historyModel.addElement(card);

        historyCountLabel.setText("History  (" + cards.size() + ")");

        if (!cards.isEmpty()) {
            currentCardView.show(cards.get(0));
        } else {
            currentCardView.showEmpty();
        }
    }

    // ── CurrentCardView ───────────────────────────────────────────────────────

    private final class CurrentCardView extends JPanel {

        private final JBLabel ruleIdLabel = new JBLabel();
        private final JBLabel phaseLabel = new JBLabel();
        private final JBLabel confidenceLabel = new JBLabel();
        private final JTextArea diagnosisArea = buildTextArea();
        private final JTextArea fixArea = buildTextArea();
        private final JButton copyFixBtn = new JButton("Copy Fix");
        private final JButton copyDiagBtn = new JButton("Copy Diagnosis");
        private final JPanel emptyState;
        private final JComponent cardState;
        private DiagnosisCard current;

        CurrentCardView() {
            super(new CardLayout());
            setBorder(JBUI.Borders.empty());

            diagnosisArea.setForeground(UIUtil.getLabelForeground());
            fixArea.setForeground(new JBColor(0x1A7F37, 0x7FD18B));
            styleChip(phaseLabel);
            styleChip(confidenceLabel);

            emptyState = buildEmptyState();
            cardState = buildCardState();

            add(emptyState, "empty");
            add(cardState, "card");
            showEmpty();
        }

        private JPanel buildEmptyState() {
            JPanel p = new JPanel(new GridBagLayout());
            JBLabel msg = new JBLabel(
                "<html><center><br>Run a Spring Boot app or test.<br>" +
                "Errors will appear here automatically.</center></html>",
                SwingConstants.CENTER);
            msg.setForeground(UIUtil.getContextHelpForeground());
            p.add(msg, new GridBagConstraints());
            return p;
        }

        private JComponent buildCardState() {
            // Content panel: a vertical stack that hugs the top, so a short card does not
            // float in the middle of a tall pane (the "area too big" feel).
            JPanel content = new JPanel(new GridBagLayout());
            content.setBorder(JBUI.Borders.empty(12, 14));
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
            c.anchor = GridBagConstraints.NORTHWEST;

            // header chips
            JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            header.setOpaque(false);
            ruleIdLabel.setFont(ruleIdLabel.getFont().deriveFont(Font.BOLD).deriveFont(JBUIScale.scale(14f)));
            header.add(ruleIdLabel);
            header.add(phaseLabel);
            header.add(confidenceLabel);
            c.gridy = 0; c.insets = JBUI.insets(0, 0, 10, 0);
            content.add(header, c);

            c.gridy = 1; c.insets = JBUI.insets(0, 0, 2, 0);
            content.add(sectionLabel("DIAGNOSIS"), c);
            c.gridy = 2; c.insets = JBUI.insets(0, 0, 12, 0);
            content.add(diagnosisArea, c);

            c.gridy = 3; c.insets = JBUI.insets(0, 0, 2, 0);
            content.add(sectionLabel("FIX"), c);
            c.gridy = 4; c.insets = JBUI.insets(0, 0, 12, 0);
            content.add(fixArea, c);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            actions.setOpaque(false);
            copyDiagBtn.addActionListener(e -> copyToClipboard(current == null ? "" : current.getDiagnosisSentence()));
            copyFixBtn.addActionListener(e -> copyToClipboard(current == null ? "" : current.getFixSentence()));
            actions.add(copyDiagBtn);
            actions.add(copyFixBtn);
            c.gridy = 5; c.insets = JBUI.insets(0);
            content.add(actions, c);

            // filler so the stack stays top-aligned
            c.gridy = 6; c.weighty = 1.0; c.fill = GridBagConstraints.BOTH;
            JPanel filler = new JPanel();
            filler.setOpaque(false);
            content.add(filler, c);

            JScrollPane scroll = ScrollPaneFactory.createScrollPane(content, true);
            scroll.setBorder(JBUI.Borders.empty());
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            return scroll;
        }

        private void styleChip(JBLabel label) {
            label.setOpaque(true);
            label.setBackground(new JBColor(0xEDEFF2, 0x3C3F41));
            label.setBorder(JBUI.Borders.empty(1, 7));
            label.setFont(label.getFont().deriveFont(Font.BOLD).deriveFont(JBUIScale.scale(10.5f)));
        }

        private JBLabel sectionLabel(String text) {
            JBLabel lbl = new JBLabel(text);
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD).deriveFont(JBUIScale.scale(10.5f)));
            lbl.setForeground(UIUtil.getContextHelpForeground());
            return lbl;
        }

        void show(DiagnosisCard card) {
            this.current = card;
            ruleIdLabel.setText("[" + card.getRuleId() + "]");
            phaseLabel.setText(card.getPhase().name());
            phaseLabel.setForeground(phaseColor(card.getPhase().name()));
            confidenceLabel.setText(card.getConfidence().name());
            confidenceLabel.setForeground(confidenceColor(card.getConfidence()));
            diagnosisArea.setText(card.getDiagnosisSentence());
            fixArea.setText(card.getFixSentence());
            diagnosisArea.setCaretPosition(0);
            fixArea.setCaretPosition(0);
            ((CardLayout) getLayout()).show(this, "card");
        }

        void showEmpty() {
            this.current = null;
            ((CardLayout) getLayout()).show(this, "empty");
        }

        private JTextArea buildTextArea() {
            JTextArea area = new JTextArea();
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setEditable(false);
            area.setOpaque(false);
            // Scaled so it respects HiDPI and the user's UI font instead of a tiny fixed size.
            area.setFont(UIUtil.getLabelFont().deriveFont(JBUIScale.scale(13.5f)));
            area.setBorder(JBUI.Borders.empty());
            return area;
        }

        private void copyToClipboard(String text) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
        }

        private Color phaseColor(String phase) {
            return switch (phase) {
                case "COMPILE" -> new JBColor(0x8C4700, 0xFFA070);
                case "STARTUP" -> new JBColor(0x7A0000, 0xFF7070);
                case "RUNTIME" -> new JBColor(0x004080, 0x70A0FF);
                case "TEST"    -> new JBColor(0x006040, 0x70FFB0);
                default        -> UIUtil.getLabelForeground();
            };
        }

        private Color confidenceColor(Confidence c) {
            return switch (c) {
                case HIGH   -> new JBColor(0x006600, 0x80D080);
                case MEDIUM -> new JBColor(0x885500, 0xFFCC70);
                default     -> new JBColor(0x555555, 0xAAAAAA);
            };
        }
    }

    // ── HistoryCellRenderer ───────────────────────────────────────────────────

    private static final class HistoryCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof DiagnosisCard card) {
                String diagnosis = StringUtil.shortenTextWithEllipsis(card.getDiagnosisSentence(), 80, 0);
                setText("<html><b>[" + card.getRuleId() + "]</b>  "
                    + "<span style='color:gray'>" + card.getPhase().name() + "</span>  "
                    + diagnosis + "</html>");
                setBorder(JBUI.Borders.empty(3, 6));
            }
            return this;
        }
    }
}
