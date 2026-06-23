package com.springdebugger.rule;

import com.springdebugger.model.Confidence;
import com.springdebugger.model.Phase;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads and provides access to all rules from spring-boot-rules.yaml.
 * Uses a raw Map approach to avoid SnakeYAML type-binding edge cases.
 */
public final class RuleCatalog {

    private static final String RULES_RESOURCE = "/rules/spring-boot-rules.yaml";

    private final List<Rule> rules;
    private final Map<String, Rule> byId;

    private RuleCatalog(List<Rule> rules) {
        this.rules = List.copyOf(rules);
        this.byId = rules.stream().collect(Collectors.toUnmodifiableMap(Rule::getId, r -> r));
    }

    public static RuleCatalog load() {
        try (InputStream stream = RuleCatalog.class.getResourceAsStream(RULES_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Rule catalog not found: " + RULES_RESOURCE);
            }
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(stream);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawRules = (List<Map<String, Object>>) root.get("rules");
            List<Rule> rules = rawRules == null
                    ? Collections.emptyList()
                    : rawRules.stream().map(RuleCatalog::mapToRule).collect(Collectors.toList());
            return new RuleCatalog(rules);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load rule catalog", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Rule mapToRule(Map<String, Object> m) {
        Rule rule = new Rule();
        rule.setId(str(m, "id"));
        rule.setName(str(m, "name"));
        rule.setStatus(str(m, "status"));
        rule.setDiagnosis(str(m, "diagnosis"));
        rule.setFix(str(m, "fix"));
        rule.setFixture(str(m, "fixture"));

        String confStr = str(m, "confidence");
        rule.setConfidence(confStr != null ? Confidence.valueOf(confStr) : Confidence.MEDIUM);

        List<String> phaseStrs = (List<String>) m.get("phases");
        if (phaseStrs != null) {
            rule.setPhases(phaseStrs.stream().map(Phase::valueOf).collect(Collectors.toList()));
        }

        rule.setTaps((List<String>) m.getOrDefault("taps", Collections.emptyList()));

        Map<String, Object> signalsMap = (Map<String, Object>) m.get("signals");
        if (signalsMap != null) {
            rule.setSignals(mapToSignals(signalsMap));
        }

        return rule;
    }

    private static SignalCriteria mapToSignals(Map<String, Object> m) {
        SignalCriteria s = new SignalCriteria();
        s.setCausedByClass(str(m, "causedByClass"));
        s.setCausedByMessage(str(m, "causedByMessage"));
        s.setMessageContains(str(m, "messageContains"));
        s.setBannerDescriptionContains(str(m, "bannerDescriptionContains"));
        s.setBannerActionContains(str(m, "bannerActionContains"));
        s.setBuildLineContains(str(m, "buildLineContains"));
        s.setExceptionClass(str(m, "exceptionClass"));
        Object httpStatus = m.get("httpStatus");
        if (httpStatus instanceof Number) {
            s.setHttpStatus(((Number) httpStatus).intValue());
        }
        return s;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    public List<Rule> all() { return rules; }
    public Rule byId(String id) { return byId.get(id); }
    public int size() { return rules.size(); }

    /** Number of validated, active rules (status DONE). This is what the engine actually evaluates. */
    public int activeCount() {
        int n = 0;
        for (Rule r : rules) {
            if ("DONE".equals(r.getStatus())) n++;
        }
        return n;
    }
}
