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
 * Suggests keeping {@code @Transactional} on service methods only: flags it on a class, or on a
 * method of a class, annotated with one of the "not a service" markers ({@code @RestController},
 * {@code @Repository} by default).
 *
 * <p>Params: {@code transactionalAnnotations} default {@code [Transactional]}; {@code
 * excludedAnnotations} default {@code [RestController, Repository]}.
 */
public final class TransactionalMisplacedCheck implements ConventionCheck {

    @Override
    public String checkType() { return "transactionalMisplaced"; }

    @Override
    public List<Violation> check(PsiFile file, ConventionRule rule) {
        if (!(file instanceof PsiJavaFile)) return List.of();

        Set<String> transactionalNames = RuleParams.stringSet(rule, "transactionalAnnotations", Set.of("Transactional"));
        Set<String> excludedNames = RuleParams.stringSet(rule, "excludedAnnotations", Set.of("RestController", "Repository"));

        List<Violation> out = new ArrayList<>();
        for (PsiClass psiClass : PsiTreeUtil.findChildrenOfType(file, PsiClass.class)) {
            String excludedMarker = excludedMarker(psiClass, excludedNames);
            if (excludedMarker == null) continue;

            findTransactional(psiClass.getModifierList(), transactionalNames).ifPresent(annotation ->
                    out.add(violation(rule, annotation, psiClass.getName(), excludedMarker)));

            for (PsiMethod method : psiClass.getMethods()) {
                findTransactional(method.getModifierList(), transactionalNames).ifPresent(annotation ->
                        out.add(violation(rule, annotation, method.getName(), excludedMarker)));
            }
        }
        return out;
    }

    private static String excludedMarker(PsiClass psiClass, Set<String> excludedNames) {
        PsiModifierList modifiers = psiClass.getModifierList();
        if (modifiers == null) return null;
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            for (String name : excludedNames) {
                if (RuleParams.matchesSimpleName(annotation, name)) return name;
            }
        }
        return null;
    }

    private static java.util.Optional<PsiAnnotation> findTransactional(PsiModifierList modifiers, Set<String> transactionalNames) {
        if (modifiers == null) return java.util.Optional.empty();
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (RuleParams.matchesAnySimpleName(annotation, transactionalNames)) return java.util.Optional.of(annotation);
        }
        return java.util.Optional.empty();
    }

    private static Violation violation(ConventionRule rule, PsiAnnotation annotation, String memberName, String excludedMarker) {
        return new Violation(annotation,
                interpolate(rule.getMessage(), memberName, excludedMarker),
                interpolate(rule.getFix(), memberName, excludedMarker));
    }

    private static String interpolate(String template, String member, String marker) {
        if (template == null) return "";
        return template.replace("{{member}}", member).replace("{{marker}}", marker);
    }
}
