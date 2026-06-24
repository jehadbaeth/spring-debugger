package com.springdebugger.terminal;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;

import java.awt.Component;
import java.awt.Container;

/**
 * Best-effort reader for the new (Gen2/reworked) IntelliJ terminal, which the classic
 * {@code JBTerminalWidget} API cannot see. Rather than bind to the terminal's internal classes
 * ({@code org.jetbrains.plugins.terminal.exp/block.*}), which are repackaged almost every release,
 * this reads whatever {@link Editor} backs the selected Terminal tab and returns its document text.
 * Reading an editor document is a stable platform operation, so this is the least-fragile hook
 * available, but it is still unverified across IDE versions: it degrades to {@code null} when no
 * editor is found, and callers fall back to the classic reader.
 *
 * <p>Gated behind an opt-in "experimental" setting precisely because it cannot be unit-tested
 * outside a live IDE.
 */
public final class EditorTerminalReader {

    private EditorTerminalReader() {}

    /** Text of the editor backing the selected Terminal tab, or null if none can be read. */
    public static String readSelectedTerminal(Project project) {
        try {
            ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow("Terminal");
            if (tw == null) return null;
            Content content = tw.getContentManager().getSelectedContent();
            if (content == null) return null;
            Editor editor = findEditor(content.getComponent());
            if (editor == null) return null;
            return ReadAction.compute(() -> editor.getDocument().getText());
        } catch (Throwable t) {
            return null;
        }
    }

    /** True when the new terminal appears readable here (an editor backs the selected tab). */
    public static boolean isAvailable(Project project) {
        try {
            ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow("Terminal");
            if (tw == null) return false;
            Content content = tw.getContentManager().getSelectedContent();
            return content != null && findEditor(content.getComponent()) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Editor findEditor(Component root) {
        if (root instanceof EditorComponentImpl) {
            return ((EditorComponentImpl) root).getEditor();
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                Editor found = findEditor(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}
