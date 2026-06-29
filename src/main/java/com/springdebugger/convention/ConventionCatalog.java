package com.springdebugger.convention;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads and provides access to all rules from conventions.yaml. Mirrors {@link
 * com.springdebugger.rule.RuleCatalog}: a raw-map parse to avoid SnakeYAML type-binding edge cases.
 * This is the source-convention catalog, entirely separate from the log diagnoser's rule catalog.
 */
public final class ConventionCatalog {

    private static final String RESOURCE = "/rules/conventions.yaml";

    private final List<ConventionRule> rules;
    private final Map<String, ConventionRule> byId;

    private ConventionCatalog(List<ConventionRule> rules) {
        this.rules = List.copyOf(rules);
        this.byId = rules.stream().collect(Collectors.toUnmodifiableMap(ConventionRule::getId, r -> r));
    }

    public static ConventionCatalog load() {
        try (InputStream stream = ConventionCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Convention catalog not found: " + RESOURCE);
            }
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(stream);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawRules =
                    root == null ? null : (List<Map<String, Object>>) root.get("rules");
            List<ConventionRule> rules = rawRules == null
                    ? Collections.emptyList()
                    : rawRules.stream().map(ConventionCatalog::mapToRule).collect(Collectors.toList());
            return new ConventionCatalog(rules);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load convention catalog", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static ConventionRule mapToRule(Map<String, Object> m) {
        ConventionRule r = new ConventionRule();
        r.setId(str(m, "id"));
        r.setName(str(m, "name"));
        r.setCheckType(str(m, "checkType"));

        Object enabled = m.get("enabled");
        r.setEnabled(enabled == null || Boolean.parseBoolean(enabled.toString()));

        String severity = str(m, "severity");
        r.setSeverity(severity != null ? severity : "WARNING");

        Object appliesTo = m.get("appliesTo");
        if (appliesTo instanceof List) {
            r.setAppliesTo((List<String>) appliesTo);
        }

        Object params = m.get("params");
        if (params instanceof Map) {
            r.setParams((Map<String, Object>) params);
        }

        r.setMessage(str(m, "message"));
        r.setFix(str(m, "fix"));
        r.setStatus(str(m, "status"));
        return r;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    public List<ConventionRule> all() { return rules; }
    public ConventionRule byId(String id) { return byId.get(id); }
    public int size() { return rules.size(); }
}
