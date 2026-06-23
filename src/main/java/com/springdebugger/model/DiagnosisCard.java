package com.springdebugger.model;

/**
 * The final output of the classification pipeline.
 * Both the offline rule engine and the future LLM adapter produce this type.
 */
public final class DiagnosisCard {

    private final String ruleId;
    private final Phase phase;
    private final String diagnosisSentence;
    private final String fixSentence;
    private final Confidence confidence;
    private final String excerpt;

    public DiagnosisCard(
            String ruleId,
            Phase phase,
            String diagnosisSentence,
            String fixSentence,
            Confidence confidence,
            String excerpt) {
        this.ruleId = ruleId;
        this.phase = phase;
        this.diagnosisSentence = diagnosisSentence;
        this.fixSentence = fixSentence;
        this.confidence = confidence;
        this.excerpt = excerpt;
    }

    /** Stable identity for de-duplication and history grouping (same rule + same diagnosis). */
    public String groupingKey() {
        return ruleId + "|" + diagnosisSentence;
    }

    public String getRuleId() { return ruleId; }
    public Phase getPhase() { return phase; }
    public String getDiagnosisSentence() { return diagnosisSentence; }
    public String getFixSentence() { return fixSentence; }
    public Confidence getConfidence() { return confidence; }
    public String getExcerpt() { return excerpt; }

    @Override
    public String toString() {
        return "[" + ruleId + "] " + diagnosisSentence + " | " + fixSentence;
    }
}
