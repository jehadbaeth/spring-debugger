package com.springdebugger.convention.checks;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.springdebugger.convention.ConventionCheck;
import com.springdebugger.convention.ConventionRule;
import com.springdebugger.convention.Violation;

import java.util.ArrayList;
import java.util.List;

/**
 * Suggests naming every {@code @Service} class or interface with a {@code Service} suffix, so the
 * business layer is easy to spot by filename.
 *
 * <p>Params: {@code serviceAnnotation} default {@code Service}; {@code suffix} default
 * {@code "Service"}.
 */
public final class ServiceClassNamingCheck implements ConventionCheck {

    @Override
    public String checkType() { return "serviceClassNaming"; }

    @Override
    public List<Violation> check(PsiFile file, ConventionRule rule) {
        if (!(file instanceof PsiJavaFile)) return List.of();

        String serviceAnnotation = RuleParams.stringParam(rule, "serviceAnnotation", "Service");
        String suffix = RuleParams.stringParam(rule, "suffix", "Service");

        List<Violation> out = new ArrayList<>();
        for (PsiClass psiClass : PsiTreeUtil.findChildrenOfType(file, PsiClass.class)) {
            PsiModifierList modifiers = psiClass.getModifierList();
            if (modifiers == null || !hasAnnotation(modifiers, serviceAnnotation)) continue;

            String name = psiClass.getName();
            if (name == null || name.endsWith(suffix)) continue;

            out.add(new Violation(psiClass.getNameIdentifier() != null ? psiClass.getNameIdentifier() : psiClass,
                    interpolate(rule.getMessage(), name, suffix),
                    interpolate(rule.getFix(), name, suffix)));
        }
        return out;
    }

    private static boolean hasAnnotation(PsiModifierList modifiers, String simpleName) {
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (RuleParams.matchesSimpleName(annotation, simpleName)) return true;
        }
        return false;
    }

    private static String interpolate(String template, String className, String suffix) {
        if (template == null) return "";
        return template.replace("{{class}}", className).replace("{{suffix}}", suffix);
    }
}
