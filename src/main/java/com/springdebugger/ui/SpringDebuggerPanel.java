package com.springdebugger.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.springdebugger.SpringDebuggerService;
import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.service.DiagnosisHistoryService;
import com.springdebugger.settings.SpringDebuggerSettings;
import com.springdebugger.settings.SpringDebuggerSettingsConfigurable;
import org.jetbrains.annotations.NotNull;

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

        JButton settingsBtn = new JButton(AllIcons.General.Settings);
        settingsBtn.setToolTipText("Open Spring Boot Debugger settings");
        settingsBtn.setBorderPainted(false);
        settingsBtn.setContentAreaFilled(false);
        settingsBtn.setFocusPainted(false);
        settingsBtn.addActionListener(e ->
            ShowSettingsUtil.getInstance().showSettingsDialog(project, SpringDebuggerSettingsConfigurable.class));
        bar.add(settingsBtn, BorderLayout.EAST);

        return bar;
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
        private final JTextArea diagnosisArea = buildTextArea(13f);
        private final JTextArea fixArea = buildTextArea(12f);
        private final JButton copyFixBtn = new JButton("Copy Fix");
        private final JButton copyDiagBtn = new JButton("Copy Diagnosis");
        private final JPanel emptyState;
        private final JPanel cardState;
        private DiagnosisCard current;

        CurrentCardView() {
            super(new CardLayout());
            setBorder(JBUI.Borders.empty(6));

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

        private JPanel buildCardState() {
            JPanel p = new JPanel(new BorderLayout(0, 6));
            p.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Borders.color"), 1, true),
                JBUI.Borders.empty(10)
            ));

            // header row
            JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            ruleIdLabel.setFont(ruleIdLabel.getFont().deriveFont(Font.BOLD));
            header.add(ruleIdLabel);
            header.add(phaseLabel);
            header.add(confidenceLabel);
            p.add(header, BorderLayout.NORTH);

            // text body
            JPanel body = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
            c.gridx = 0; c.insets = JBUI.insets(0, 0, 6, 0);

            c.gridy = 0;
            body.add(labeledArea("Diagnosis:", diagnosisArea, new JBColor(0x333333, 0xCCCCCC)), c);
            c.gridy = 1;
            body.add(labeledArea("Fix:", fixArea, new JBColor(0x005C00, 0x80D080)), c);
            p.add(body, BorderLayout.CENTER);

            // action buttons
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            copyDiagBtn.addActionListener(e -> copyToClipboard(current == null ? "" : current.getDiagnosisSentence()));
            copyFixBtn.addActionListener(e -> copyToClipboard(current == null ? "" : current.getFixSentence()));
            actions.add(copyDiagBtn);
            actions.add(copyFixBtn);
            p.add(actions, BorderLayout.SOUTH);

            return p;
        }

        private JPanel labeledArea(String labelText, JTextArea area, Color textColor) {
            JPanel p = new JPanel(new BorderLayout(0, 2));
            JBLabel lbl = new JBLabel(labelText);
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD).deriveFont(11f));
            lbl.setForeground(UIUtil.getContextHelpForeground());
            p.add(lbl, BorderLayout.NORTH);
            area.setForeground(textColor);
            p.add(area, BorderLayout.CENTER);
            return p;
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

        private JTextArea buildTextArea(float size) {
            JTextArea area = new JTextArea();
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setEditable(false);
            area.setOpaque(false);
            area.setFont(area.getFont().deriveFont(size));
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
