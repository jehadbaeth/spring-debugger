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
 * Suggests that every {@code String} field of a JPA {@code @Entity} carry a bound, so an unbounded
 * value can never reach the persistence layer. A field counts as bounded if it has any one of the
 * configured annotations (a {@code @Size}, a {@code @Column(length=...)}, or a custom
 * {@code AttributeConverter} wired via {@code @Convert}).
 *
 * <p>Params: {@code entityAnnotation} default {@code Entity}; {@code boundingAnnotations} default
 * {@code [Size, Column, Convert]}.
 */
public final class EntityStringFieldBoundedCheck implements ConventionCheck {

    @Override
    public String checkType() { return "entityStringFieldBounded"; }

    @Override
    public List<Violation> check(PsiFile file, ConventionRule rule) {
        if (!(file instanceof PsiJavaFile)) return List.of();

        String entityAnnotation = RuleParams.stringParam(rule, "entityAnnotation", "Entity");
        Set<String> boundingAnnotations = RuleParams.stringSet(rule, "boundingAnnotations", Set.of("Size", "Column", "Convert"));

        List<Violation> out = new ArrayList<>();
        for (PsiClass psiClass : PsiTreeUtil.findChildrenOfType(file, PsiClass.class)) {
            if (!hasAnnotation(psiClass.getModifierList(), entityAnnotation)) continue;

            for (PsiField field : psiClass.getFields()) {
                if (!isStringType(field.getType())) continue;
                if (hasAnyAnnotation(field.getModifierList(), boundingAnnotations)) continue;

                String name = field.getName();
                out.add(new Violation(field.getNameIdentifier() != null ? field.getNameIdentifier() : field,
                        interpolate(rule.getMessage(), name),
                        interpolate(rule.getFix(), name)));
            }
        }
        return out;
    }

    private static boolean isStringType(PsiType type) {
        return type instanceof PsiClassType classType && "String".equals(classType.getClassName());
    }

    private static boolean hasAnnotation(PsiModifierList modifiers, String simpleName) {
        if (modifiers == null) return false;
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (RuleParams.matchesSimpleName(annotation, simpleName)) return true;
        }
        return false;
    }

    private static boolean hasAnyAnnotation(PsiModifierList modifiers, Set<String> simpleNames) {
        if (modifiers == null) return false;
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (RuleParams.matchesAnySimpleName(annotation, simpleNames)) return true;
        }
        return false;
    }

    private static String interpolate(String template, String field) {
        return template == null ? "" : template.replace("{{field}}", field);
    }
}
