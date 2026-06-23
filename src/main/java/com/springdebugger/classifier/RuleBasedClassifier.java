package com.springdebugger.classifier;

import com.springdebugger.engine.DiagnosisEngine;
import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.RawSignal;
import com.springdebugger.rule.Rule;
import com.springdebugger.rule.RuleCatalog;
import com.springdebugger.rule.SignalCriteria;

import java.util.Optional;

/**
 * Matches a RawSignal against the loaded rule catalog and produces a DiagnosisCard.
 * Rules are evaluated in catalog order; the first rule where all non-null criteria match wins.
 *
 * This is the offline rule engine: the first {@link DiagnosisEngine} the pipeline tries.
 */
public final class RuleBasedClassifier implements DiagnosisEngine {

    private final RuleCatalog catalog;

    public RuleBasedClassifier(RuleCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Optional<DiagnosisCard> diagnose(RawSignal signal) {
        return classify(signal);
    }

    public Optional<DiagnosisCard> classify(RawSignal signal) {
        for (Rule rule : catalog.all()) {
            // Only validated rules are active. A rule is DONE only when it has a
            // fixture that passes ClassifierFixtureTest; TODO rules are unvalidated
            // and must not fire in production.
            if (!"DONE".equals(rule.getStatus())) {
                continue;
            }
            if (rule.getPhases() != null && !rule.getPhases().isEmpty()
                    && !rule.getPhases().contains(signal.getPhase())) {
                continue;
            }
            if (matches(rule.getSignals(), signal)) {
                String diagnosis = fillTemplate(rule.getDiagnosis(), signal, rule);
                String fix = fillTemplate(rule.getFix(), signal, rule);
                return Optional.of(new DiagnosisCard(
                        rule.getId(),
                        signal.getPhase(),
                        diagnosis,
                        fix,
                        rule.getConfidence(),
                        signal.getRawExcerpt()));
            }
        }
        return Optional.empty();
    }

    private boolean matches(SignalCriteria criteria, RawSignal signal) {
        if (criteria == null) return false;

        if (criteria.getCausedByClass() != null) {
            if (signal.getDeepestCausedByClass() == null) return false;
            if (!containsIgnoreCase(signal.getDeepestCausedByClass(), criteria.getCausedByClass())) return false;
        }

        if (criteria.getCausedByMessage() != null) {
            if (signal.getDeepestCausedByMessage() == null) return false;
            if (!containsIgnoreCase(signal.getDeepestCausedByMessage(), criteria.getCausedByMessage())) return false;
        }

        if (criteria.getMessageContains() != null) {
            boolean inLines = signal.anyLineContains(criteria.getMessageContains());
            boolean inExcerpt = containsIgnoreCase(signal.getRawExcerpt(), criteria.getMessageContains());
            if (!inLines && !inExcerpt) return false;
        }

        if (criteria.getBannerDescriptionContains() != null) {
            if (signal.getBannerDescription() == null) return false;
            if (!containsIgnoreCase(signal.getBannerDescription(), criteria.getBannerDescriptionContains())) return false;
        }

        if (criteria.getBannerActionContains() != null) {
            if (signal.getBannerAction() == null) return false;
            if (!containsIgnoreCase(signal.getBannerAction(), criteria.getBannerActionContains())) return false;
        }

        if (criteria.getHttpStatus() > 0) {
            if (signal.getHttpStatus() != criteria.getHttpStatus()) return false;
        }

        if (criteria.getBuildLineContains() != null) {
            boolean inLines = signal.anyLineContains(criteria.getBuildLineContains());
            boolean inExcerpt = containsIgnoreCase(signal.getRawExcerpt(), criteria.getBuildLineContains());
            if (!inLines && !inExcerpt) return false;
        }

        return true;
    }

    private static final java.util.regex.Pattern PORT =
            java.util.regex.Pattern.compile("[Pp]ort[\\s:]+(\\d{2,5})");
    private static final java.util.regex.Pattern PLACEHOLDER =
            java.util.regex.Pattern.compile("Could not resolve placeholder ['\"]?([\\w.\\-]+)");

    private String fillTemplate(String template, RawSignal signal, Rule rule) {
        if (template == null) return "";
        String result = template;
        if (result.contains("{{beanType}}")) {
            result = result.replace("{{beanType}}", signal.getDeepestCausedByMessage() != null
                    ? extractTypeName(signal.getDeepestCausedByMessage()) : "the required type");
        }
        if (result.contains("{{beanName}}")) {
            result = result.replace("{{beanName}}", signal.getFailingBeanName() != null
                    ? signal.getFailingBeanName() : "the failing bean");
        }
        if (result.contains("{{port}}")) {
            result = result.replace("{{port}}", firstMatch(PORT, signal, "the configured port"));
        }
        if (result.contains("{{property}}")) {
            result = result.replace("{{property}}", firstMatch(PLACEHOLDER, signal, "the property"));
        }
        result = result.replace("{{ruleId}}", rule.getId());
        return result;
    }

    /** First capture group of the pattern across the deepest message, banner, then excerpt. */
    private String firstMatch(java.util.regex.Pattern pattern, RawSignal signal, String fallback) {
        for (String text : new String[]{signal.getDeepestCausedByMessage(),
                signal.getBannerDescription(), signal.getRawExcerpt()}) {
            if (text == null) continue;
            java.util.regex.Matcher m = pattern.matcher(text);
            if (m.find()) return m.group(1);
        }
        return fallback;
    }

    private static final java.util.regex.Pattern QUOTED_TYPE =
            java.util.regex.Pattern.compile("'([\\w.$]+)'");
    private static final java.util.regex.Pattern OF_TYPE =
            java.util.regex.Pattern.compile("of type \\[?([\\w.$]+)");

    /**
     * Extracts the bean type name from an exception message, e.g. from
     * "No qualifying bean of type 'com.example.FgClassifier' available" returns "FgClassifier".
     * Prefers a quoted fully-qualified name, then an "of type X" form.
     */
    private String extractTypeName(String message) {
        java.util.regex.Matcher q = QUOTED_TYPE.matcher(message);
        while (q.find()) {
            String candidate = q.group(1);
            if (candidate.contains(".") || (!candidate.isEmpty() && Character.isUpperCase(candidate.charAt(0)))) {
                return simpleName(candidate);
            }
        }
        java.util.regex.Matcher o = OF_TYPE.matcher(message);
        if (o.find()) return simpleName(o.group(1));
        return "the required type";
    }

    private String simpleName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private boolean containsIgnoreCase(String text, String substring) {
        return text.toLowerCase().contains(substring.toLowerCase());
    }
}
