package com.springdebugger.convention;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.springdebugger.settings.SpringDebuggerSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The single umbrella inspection that surfaces all convention rules. Registered once in plugin.xml;
 * rule on/off lives in the catalog and settings, not in IntelliJ's inspection profile, so the
 * catalog stays the single source of truth for what is active (a deliberate divergence from the
 * one-inspection-per-rule idiom, see CONVENTIONS-PLAN.md section 4.4).
 *
 * <p>Driven through {@code buildVisitor} (not {@code checkFile}) because the on-the-fly highlighting
 * pass uses the visitor; {@code checkFile} only fires for batch runs.
 */
public final class ConventionInspection extends LocalInspectionTool {

    private final ConventionCatalog catalog = ConventionCatalog.load();

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                runRules(file, holder, isOnTheFly);
            }
        };
    }

    private void runRules(PsiFile file, ProblemsHolder holder, boolean isOnTheFly) {
        SpringDebuggerSettings settings = SpringDebuggerSettings.getInstance();
        if (!settings.isConventionsEnabled()) return;

        String fileType = fileType(file);
        for (ConventionRule rule : catalog.all()) {
            if (!"DONE".equals(rule.getStatus())) continue;
            if (!settings.isConventionRuleEnabled(rule.getId(), rule.isEnabled())) continue;
            if (!rule.appliesToFileType(fileType)) continue;

            ConventionCheck check = CheckRegistry.get(rule.getCheckType());
            if (check == null) continue;

            for (Violation v : check.check(file, rule)) {
                String text = v.fix() == null || v.fix().isEmpty() ? v.message() : v.message() + " " + v.fix();
                ProblemHighlightType type = highlightType(rule.getSeverity());
                ProblemDescriptor descriptor = v.range() != null
                        ? holder.getManager().createProblemDescriptor(
                                v.anchor(), v.range(), text, type, isOnTheFly, (LocalQuickFix[]) null)
                        : holder.getManager().createProblemDescriptor(
                                v.anchor(), text, isOnTheFly, (LocalQuickFix[]) null, type);
                holder.registerProblem(descriptor);
            }
        }
    }

    private static ProblemHighlightType highlightType(String severity) {
        if (severity == null) return ProblemHighlightType.WARNING;
        switch (severity.toUpperCase(Locale.ROOT)) {
            case "ERROR": return ProblemHighlightType.ERROR;
            case "WEAK_WARNING": return ProblemHighlightType.WEAK_WARNING;
            default: return ProblemHighlightType.WARNING;
        }
    }

    private static String fileType(PsiFile file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
