package com.springdebugger.convention.checks;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.springdebugger.convention.ConventionCheck;
import com.springdebugger.convention.ConventionRule;
import com.springdebugger.convention.Violation;

import java.util.ArrayList;
import java.util.List;

/**
 * Suggests not using {@code java.util.Optional} as a method parameter type or a field type, per the
 * "never use Optional as a method parameter" / "never store Optional in a field" convention.
 * Matches the type by simple name ({@code Optional}), so it also catches unresolved references in
 * classpath-less test fixtures.
 *
 * <p>Params: {@code target} either {@code "parameter"} or {@code "field"}, selecting which check
 * this rule instance runs.
 */
public final class OptionalUsageCheck implements ConventionCheck {

    @Override
    public String checkType() { return "optionalUsage"; }

    @Override
    public List<Violation> check(PsiFile file, ConventionRule rule) {
        if (!(file instanceof PsiJavaFile)) return List.of();

        String target = RuleParams.stringParam(rule, "target", "parameter");
        List<Violation> out = new ArrayList<>();

        if ("field".equals(target)) {
            for (PsiField field : PsiTreeUtil.findChildrenOfType(file, PsiField.class)) {
                if (isOptionalType(field.getType())) {
                    String name = field.getName();
                    out.add(new Violation(field.getNameIdentifier() != null ? field.getNameIdentifier() : field,
                            interpolate(rule.getMessage(), name),
                            interpolate(rule.getFix(), name)));
                }
            }
        } else {
            for (PsiParameter parameter : PsiTreeUtil.findChildrenOfType(file, PsiParameter.class)) {
                if (!(parameter.getDeclarationScope() instanceof PsiMethod)) continue;
                if (isOptionalType(parameter.getType())) {
                    String name = parameter.getName();
                    out.add(new Violation(parameter.getNameIdentifier() != null ? parameter.getNameIdentifier() : parameter,
                            interpolate(rule.getMessage(), name),
                            interpolate(rule.getFix(), name)));
                }
            }
        }
        return out;
    }

    private static boolean isOptionalType(PsiType type) {
        if (!(type instanceof PsiClassType classType)) return false;
        String name = classType.getClassName();
        return "Optional".equals(name);
    }

    private static String interpolate(String template, String name) {
        return template == null ? "" : template.replace("{{name}}", name);
    }
}
