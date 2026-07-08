package com.springdebugger.convention.checks;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.springdebugger.convention.ConventionCheck;
import com.springdebugger.convention.ConventionRule;
import com.springdebugger.convention.Violation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Suggests pairing {@code @RequestBody} with {@code @Valid} (or {@code @Validated}) so DTO bean
 * validation actually runs at the API boundary.
 *
 * <p>Params: {@code bodyAnnotation} default {@code RequestBody}; {@code validationAnnotations}
 * default {@code [Valid, Validated]}.
 */
public final class RequestBodyRequiresValidCheck implements ConventionCheck {

    @Override
    public String checkType() { return "requestBodyRequiresValid"; }

    @Override
    public List<Violation> check(PsiFile file, ConventionRule rule) {
        if (!(file instanceof PsiJavaFile)) return List.of();

        String bodyAnnotation = RuleParams.stringParam(rule, "bodyAnnotation", "RequestBody");
        Set<String> validationAnnotations = RuleParams.stringSet(rule, "validationAnnotations", Set.of("Valid", "Validated"));

        List<Violation> out = new ArrayList<>();
        for (PsiParameter parameter : PsiTreeUtil.findChildrenOfType(file, PsiParameter.class)) {
            PsiModifierList modifiers = parameter.getModifierList();
            if (modifiers == null) continue;
            if (!hasAnnotation(modifiers, bodyAnnotation)) continue;
            if (hasAnyAnnotation(modifiers, validationAnnotations)) continue;

            String name = parameter.getName();
            out.add(new Violation(parameter.getNameIdentifier() != null ? parameter.getNameIdentifier() : parameter,
                    interpolate(rule.getMessage(), name),
                    interpolate(rule.getFix(), name)));
        }
        return out;
    }

    private static boolean hasAnnotation(PsiModifierList modifiers, String simpleName) {
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (RuleParams.matchesSimpleName(annotation, simpleName)) return true;
        }
        return false;
    }

    private static boolean hasAnyAnnotation(PsiModifierList modifiers, Set<String> simpleNames) {
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (RuleParams.matchesAnySimpleName(annotation, simpleNames)) return true;
        }
        return false;
    }

    private static String interpolate(String template, String parameter) {
        return template == null ? "" : template.replace("{{parameter}}", parameter);
    }
}
