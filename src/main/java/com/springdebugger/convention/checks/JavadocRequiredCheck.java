package com.springdebugger.convention.checks;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.springdebugger.convention.ConventionCheck;
import com.springdebugger.convention.ConventionRule;
import com.springdebugger.convention.Violation;

import java.util.*;

/**
 * Flags methods that have no Javadoc comment. Pure PSI traversal: no IO, no cross-file resolution.
 *
 * <p>Params (from the catalog):
 * <ul>
 *   <li>{@code visibilities} list of {@code public|protected|package|private}; a method requires
 *       Javadoc only if its visibility is in this list. Default {@code [public]}.</li>
 *   <li>{@code skipOverrides} if true, methods annotated {@code @Override} are exempt. Default true.</li>
 *   <li>{@code skipAccessors} if true, trivial getters/setters are exempt. Default true.</li>
 * </ul>
 * Constructors are never flagged: "method Javadoc" does not target them.
 */
public final class JavadocRequiredCheck implements ConventionCheck {

    @Override
    public String checkType() { return "javadocRequired"; }

    @Override
    public List<Violation> check(PsiFile file, ConventionRule rule) {
        if (!(file instanceof PsiJavaFile)) return List.of();

        Set<String> visibilities = visibilities(rule);
        boolean skipOverrides = boolParam(rule, "skipOverrides", true);
        boolean skipAccessors = boolParam(rule, "skipAccessors", true);

        List<Violation> out = new ArrayList<>();
        for (PsiMethod method : PsiTreeUtil.findChildrenOfType(file, PsiMethod.class)) {
            if (method.isConstructor()) continue;
            if (!visibilityMatches(method, visibilities)) continue;
            if (skipOverrides && hasOverride(method)) continue;
            if (skipAccessors && isAccessor(method)) continue;
            if (method.getDocComment() != null) continue;

            PsiElement anchor = method.getNameIdentifier();
            if (anchor == null) anchor = method;
            String name = method.getName();
            out.add(new Violation(anchor,
                    interpolate(rule.getMessage(), name),
                    interpolate(rule.getFix(), name)));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> visibilities(ConventionRule rule) {
        Map<String, Object> params = rule.getParams();
        Object v = params == null ? null : params.get("visibilities");
        if (v instanceof List) {
            Set<String> set = new HashSet<>();
            for (Object o : (List<Object>) v) set.add(String.valueOf(o));
            if (!set.isEmpty()) return set;
        }
        return Set.of("public");
    }

    private static boolean boolParam(ConventionRule rule, String key, boolean dflt) {
        Map<String, Object> params = rule.getParams();
        Object v = params == null ? null : params.get(key);
        return v == null ? dflt : Boolean.parseBoolean(v.toString());
    }

    private static boolean visibilityMatches(PsiMethod m, Set<String> vis) {
        if (m.hasModifierProperty(PsiModifier.PUBLIC)) return vis.contains("public");
        if (m.hasModifierProperty(PsiModifier.PROTECTED)) return vis.contains("protected");
        if (m.hasModifierProperty(PsiModifier.PRIVATE)) return vis.contains("private");
        return vis.contains("package"); // package-private (no explicit modifier)
    }

    private static boolean hasOverride(PsiMethod m) {
        for (PsiAnnotation annotation : m.getModifierList().getAnnotations()) {
            // Match by qualified name when it resolves, and fall back to the short reference name so
            // the check does not depend on @Override resolving against the JDK.
            if ("java.lang.Override".equals(annotation.getQualifiedName())) return true;
            PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
            if (ref != null && "Override".equals(ref.getReferenceName())) return true;
        }
        return false;
    }

    private static boolean isAccessor(PsiMethod m) {
        String n = m.getName();
        int params = m.getParameterList().getParametersCount();
        PsiType ret = m.getReturnType();
        boolean isVoid = PsiTypes.voidType().equals(ret);
        if (n.startsWith("get") && n.length() > 3 && params == 0 && ret != null && !isVoid) return true;
        if (n.startsWith("is") && n.length() > 2 && params == 0 && PsiTypes.booleanType().equals(ret)) return true;
        return n.startsWith("set") && n.length() > 3 && params == 1 && isVoid;
    }

    private static String interpolate(String template, String method) {
        return template == null ? "" : template.replace("{{method}}", method);
    }
}
