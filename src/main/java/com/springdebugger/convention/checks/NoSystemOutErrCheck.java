package com.springdebugger.convention.checks;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.springdebugger.convention.ConventionCheck;
import com.springdebugger.convention.ConventionRule;
import com.springdebugger.convention.Violation;

import java.util.ArrayList;
import java.util.List;

/**
 * Suggests a logger over {@code System.out} / {@code System.err}: flags any reference expression
 * whose qualifier is {@code System} and whose member is {@code out} or {@code err}. Matched
 * textually (no classpath resolution needed), so it works the same in test fixtures and real
 * projects.
 */
public final class NoSystemOutErrCheck implements ConventionCheck {

    @Override
    public String checkType() { return "noSystemOutErr"; }

    @Override
    public List<Violation> check(PsiFile file, ConventionRule rule) {
        if (!(file instanceof PsiJavaFile)) return List.of();

        List<Violation> out = new ArrayList<>();
        for (PsiReferenceExpression ref : PsiTreeUtil.findChildrenOfType(file, PsiReferenceExpression.class)) {
            String member = ref.getReferenceName();
            if (!"out".equals(member) && !"err".equals(member)) continue;

            PsiExpression qualifier = ref.getQualifierExpression();
            if (!(qualifier instanceof PsiReferenceExpression qualifierRef)) continue;
            if (!"System".equals(qualifierRef.getReferenceName())) continue;
            if (qualifierRef.getQualifierExpression() != null) continue;

            out.add(new Violation(ref,
                    interpolate(rule.getMessage(), member),
                    interpolate(rule.getFix(), member)));
        }
        return out;
    }

    private static String interpolate(String template, String member) {
        return template == null ? "" : template.replace("{{member}}", member);
    }
}
