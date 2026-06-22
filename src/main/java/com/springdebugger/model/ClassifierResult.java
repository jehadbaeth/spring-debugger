package com.springdebugger.model;

import java.util.Optional;

/**
 * Intermediate result from the classifier before template substitution in the synthesizer.
 */
public final class ClassifierResult {

    public static final ClassifierResult NO_MATCH =
            new ClassifierResult(null, null, Confidence.NONE);

    private final String ruleId;
    private final String matchedRuleName;
    private final Confidence confidence;

    public ClassifierResult(String ruleId, String matchedRuleName, Confidence confidence) {
        this.ruleId = ruleId;
        this.matchedRuleName = matchedRuleName;
        this.confidence = confidence;
    }

    public boolean isMatch() { return confidence != Confidence.NONE; }
    public Optional<String> getRuleId() { return Optional.ofNullable(ruleId); }
    public Optional<String> getMatchedRuleName() { return Optional.ofNullable(matchedRuleName); }
    public Confidence getConfidence() { return confidence; }
}
