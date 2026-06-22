package com.springdebugger.model;

import java.util.List;

/**
 * Structured data extracted from a raw log or build output stream before classification.
 */
public final class RawSignal {

    private final Phase phase;

    /** The fully qualified class name of the deepest Caused by exception, or null. */
    private final String deepestCausedByClass;

    /** The message of the deepest Caused by exception, or null. */
    private final String deepestCausedByMessage;

    /** The Description: field from the Spring Boot FailureAnalyzer banner, or null. */
    private final String bannerDescription;

    /** The Action: field from the Spring Boot FailureAnalyzer banner, or null. */
    private final String bannerAction;

    /** The bean name extracted from "Error creating bean with name '...'" lines, or null. */
    private final String failingBeanName;

    /** HTTP status code extracted from web layer errors, or -1 if not applicable. */
    private final int httpStatus;

    /** Raw lines from the output that the extractor considers relevant. */
    private final List<String> relevantLines;

    /** The full raw excerpt used to produce this signal (a bounded window of the output). */
    private final String rawExcerpt;

    public RawSignal(
            Phase phase,
            String deepestCausedByClass,
            String deepestCausedByMessage,
            String bannerDescription,
            String bannerAction,
            String failingBeanName,
            int httpStatus,
            List<String> relevantLines,
            String rawExcerpt) {
        this.phase = phase;
        this.deepestCausedByClass = deepestCausedByClass;
        this.deepestCausedByMessage = deepestCausedByMessage;
        this.bannerDescription = bannerDescription;
        this.bannerAction = bannerAction;
        this.failingBeanName = failingBeanName;
        this.httpStatus = httpStatus;
        this.relevantLines = List.copyOf(relevantLines);
        this.rawExcerpt = rawExcerpt;
    }

    public Phase getPhase() { return phase; }
    public String getDeepestCausedByClass() { return deepestCausedByClass; }
    public String getDeepestCausedByMessage() { return deepestCausedByMessage; }
    public String getBannerDescription() { return bannerDescription; }
    public String getBannerAction() { return bannerAction; }
    public String getFailingBeanName() { return failingBeanName; }
    public int getHttpStatus() { return httpStatus; }
    public List<String> getRelevantLines() { return relevantLines; }
    public String getRawExcerpt() { return rawExcerpt; }

    public boolean hasDeepestCause() { return deepestCausedByClass != null; }
    public boolean hasBanner() { return bannerDescription != null; }

    /** Returns true if any relevant line contains the given substring (case-insensitive). */
    public boolean anyLineContains(String substring) {
        String lower = substring.toLowerCase();
        return relevantLines.stream().anyMatch(l -> l.toLowerCase().contains(lower));
    }
}
