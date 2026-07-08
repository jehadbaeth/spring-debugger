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
 * Suggests constructor injection over field injection: flags fields annotated with an
 * injection annotation ({@code @Autowired}, {@code @Inject} by default). Pure PSI traversal, no
 * cross-file resolution.
 *
 * <p>Params: {@code annotations} list of simple annotation names that count as field injection.
 * Default {@code [Autowired, Inject]}.
 */
public final class FieldInjectionForbiddenCheck implements ConventionCheck {

    @Override
    public String checkType() { return "fieldInjectionForbidden"; }

    @Override
    public List<Violation> check(PsiFile file, ConventionRule rule) {
        if (!(file instanceof PsiJavaFile)) return List.of();

        Set<String> annotationNames = RuleParams.stringSet(rule, "annotations", Set.of("Autowired", "Inject"));

        List<Violation> out = new ArrayList<>();
        for (PsiField field : PsiTreeUtil.findChildrenOfType(file, PsiField.class)) {
            PsiModifierList modifiers = field.getModifierList();
            if (modifiers == null) continue;
            for (PsiAnnotation annotation : modifiers.getAnnotations()) {
                if (RuleParams.matchesAnySimpleName(annotation, annotationNames)) {
                    String name = field.getName();
                    out.add(new Violation(field.getNameIdentifier() != null ? field.getNameIdentifier() : field,
                            interpolate(rule.getMessage(), name),
                            interpolate(rule.getFix(), name)));
                    break;
                }
            }
        }
        return out;
    }

    private static String interpolate(String template, String field) {
        return template == null ? "" : template.replace("{{field}}", field);
    }
}
