package com.springdebugger.convention.checks;

import com.intellij.openapi.util.TextRange;
import com.springdebugger.convention.ConventionRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shared helpers for the Robot convention checks: param reads, range math, message interpolation. */
final class RobotChecks {

    private RobotChecks() {}

    @SuppressWarnings("unchecked")
    static List<String> stringList(ConventionRule rule, String key, List<String> dflt) {
        Map<String, Object> params = rule.getParams();
        Object v = params == null ? null : params.get(key);
        if (v instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object o : (List<Object>) v) out.add(String.valueOf(o));
            if (!out.isEmpty()) return out;
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

    /** Range of the first line of the text, capped so a highlight on an empty/odd file stays sane. */
    static TextRange firstLineRange(String text) {
        int end = text.indexOf('\n');
        if (end < 0) end = text.length();
        if (end > 80) end = 80;
        return new TextRange(0, Math.max(0, end));
    }

    static String interpolate(String template, String key, String value) {
        return template == null ? "" : template.replace("{{" + key + "}}", value);
    }
}
