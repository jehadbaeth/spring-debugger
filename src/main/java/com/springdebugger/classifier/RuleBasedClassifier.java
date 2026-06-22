package com.springdebugger.classifier;

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
 */
public final class RuleBasedClassifier {

    private final RuleCatalog catalog;

    public RuleBasedClassifier(RuleCatalog catalog) {
        this.catalog = catalog;
    }

    public Optional<DiagnosisCard> classify(RawSignal signal) {
        for (Rule rule : catalog.all()) {
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
            if (!signal.anyLineContains(criteria.getMessageContains())) return false;
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
            if (!signal.anyLineContains(criteria.getBuildLineContains())) return false;
        }

        return true;
    }

    private String fillTemplate(String template, RawSignal signal, Rule rule) {
        if (template == null) return "";
        String result = template;
        result = result.replace("{{beanType}}", signal.getDeepestCausedByMessage() != null
                ? extractTypeName(signal.getDeepestCausedByMessage()) : "unknown type");
        result = result.replace("{{beanName}}", signal.getFailingBeanName() != null
                ? signal.getFailingBeanName() : "unknown bean");
        result = result.replace("{{ruleId}}", rule.getId());
        return result;
    }

    private String extractTypeName(String message) {
        int lastDot = message.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < message.length() - 1) {
            String candidate = message.substring(lastDot + 1).split("[^a-zA-Z0-9$_]")[0];
            if (!candidate.isEmpty()) return candidate;
        }
        return message.length() > 80 ? message.substring(0, 80) + "..." : message;
    }

    private boolean containsIgnoreCase(String text, String substring) {
        return text.toLowerCase().contains(substring.toLowerCase());
    }
}
