package com.springdebugger.convention.checks;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.springdebugger.convention.ConventionCheck;
import com.springdebugger.convention.ConventionRule;
import com.springdebugger.convention.Violation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Suggests versioning every controller's base path (e.g. {@code /api/v1/...}) per the API
 * versioning convention. Only looks at the class-level mapping annotation's {@code value} or
 * {@code path} attribute; only flags when the attribute is a literal string, so an expression we
 * cannot read (a constant reference, a concatenation) is silently skipped rather than guessed at.
 *
 * <p>Params: {@code mappingAnnotations} default {@code [RequestMapping]}; {@code pathPattern}
 * default {@code ^/api/v\d+(/.*)?$}.
 */
public final class ApiVersionPathCheck implements ConventionCheck {

    @Override
    public String checkType() { return "apiVersionPath"; }

    @Override
    public List<Violation> check(PsiFile file, ConventionRule rule) {
        if (!(file instanceof PsiJavaFile)) return List.of();

        Set<String> mappingAnnotations = RuleParams.stringSet(rule, "mappingAnnotations", Set.of("RequestMapping"));
        Pattern pathPattern = Pattern.compile(RuleParams.stringParam(rule, "pathPattern", "^/api/v\\d+(/.*)?$"));

        List<Violation> out = new ArrayList<>();
        for (PsiClass psiClass : PsiTreeUtil.findChildrenOfType(file, PsiClass.class)) {
            PsiModifierList modifiers = psiClass.getModifierList();
            if (modifiers == null) continue;

            for (PsiAnnotation annotation : modifiers.getAnnotations()) {
                if (!RuleParams.matchesAnySimpleName(annotation, mappingAnnotations)) continue;

                String path = literalPathValue(annotation);
                if (path == null || pathPattern.matcher(path).matches()) continue;

                String className = psiClass.getName();
                out.add(new Violation(annotation,
                        interpolate(rule.getMessage(), path, className),
                        interpolate(rule.getFix(), path, className)));
            }
        }
        return out;
    }

    private static String literalPathValue(PsiAnnotation annotation) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (value == null) value = annotation.findAttributeValue("path");
        return literalString(value);
    }

    private static String literalString(PsiAnnotationMemberValue value) {
        if (value instanceof PsiArrayInitializerMemberValue array) {
            PsiAnnotationMemberValue[] initializers = array.getInitializers();
            return initializers.length == 1 ? literalString(initializers[0]) : null;
        }
        if (value instanceof PsiLiteralExpression literal) {
            Object literalValue = literal.getValue();
            return literalValue instanceof String ? (String) literalValue : null;
        }
        return null;
    }

    private static String interpolate(String template, String path, String className) {
        if (template == null) return "";
        return template.replace("{{path}}", path).replace("{{class}}", className);
    }
}
