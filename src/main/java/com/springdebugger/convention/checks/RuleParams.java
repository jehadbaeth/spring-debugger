package com.springdebugger.convention.checks;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.springdebugger.convention.ConventionRule;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared param-reading and annotation-matching helpers for the Spring Boot convention checks.
 * Annotations are matched by simple name, not qualified name: test fixtures built with
 * {@code LightJavaCodeInsightFixtureTestCase} have no Spring/Jakarta classpath, so a qualified-name
 * resolution would silently no-op in tests while working in a real project. Matching by simple name
 * keeps behavior identical in both.
 */
final class RuleParams {

    private RuleParams() {}

    @SuppressWarnings("unchecked")
    static Set<String> stringSet(ConventionRule rule, String key, Set<String> dflt) {
        Map<String, Object> params = rule.getParams();
        Object v = params == null ? null : params.get(key);
        if (v instanceof List) {
            Set<String> set = new HashSet<>();
            for (Object o : (List<Object>) v) set.add(String.valueOf(o));
            if (!set.isEmpty()) return set;
        }
        return dflt;
    }

    static String stringParam(ConventionRule rule, String key, String dflt) {
        Map<String, Object> params = rule.getParams();
        Object v = params == null ? null : params.get(key);
        return v == null ? dflt : v.toString();
    }

    static boolean boolParam(ConventionRule rule, String key, boolean dflt) {
        Map<String, Object> params = rule.getParams();
        Object v = params == null ? null : params.get(key);
        return v == null ? dflt : Boolean.parseBoolean(v.toString());
    }

    static boolean matchesSimpleName(PsiAnnotation annotation, String simpleName) {
        PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
        if (ref != null && simpleName.equals(ref.getReferenceName())) return true;
        String qualified = annotation.getQualifiedName();
        return qualified != null && qualified.equals(simpleName);
    }

    static boolean matchesAnySimpleName(PsiAnnotation annotation, Set<String> simpleNames) {
        for (String name : simpleNames) {
            if (matchesSimpleName(annotation, name)) return true;
        }
        return false;
    }
}
