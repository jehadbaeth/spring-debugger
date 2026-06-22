package com.springdebugger.rule;

/**
 * Mirrors the signals block in spring-boot-rules.yaml.
 * All fields are optional; a rule fires when every non-null field matches.
 */
public final class SignalCriteria {

    /** Substring match against the deepest Caused by exception class name. */
    private String causedByClass;

    /** Substring match against the deepest Caused by message. */
    private String causedByMessage;

    /** Substring match against any relevant line in the signal. */
    private String messageContains;

    /** Substring match against the FailureAnalyzer banner Description field. */
    private String bannerDescriptionContains;

    /** Substring match against the FailureAnalyzer banner Action field. */
    private String bannerActionContains;

    /** Substring match against a build output line (BUILD_OUTPUT tap). */
    private String buildLineContains;

    /** Exact HTTP status code, or 0 if not applicable. */
    private int httpStatus;

    /** Substring match against the top-level (outermost) exception class name. */
    private String exceptionClass;

    public String getCausedByClass() { return causedByClass; }
    public void setCausedByClass(String causedByClass) { this.causedByClass = causedByClass; }

    public String getCausedByMessage() { return causedByMessage; }
    public void setCausedByMessage(String causedByMessage) { this.causedByMessage = causedByMessage; }

    public String getMessageContains() { return messageContains; }
    public void setMessageContains(String messageContains) { this.messageContains = messageContains; }

    public String getBannerDescriptionContains() { return bannerDescriptionContains; }
    public void setBannerDescriptionContains(String v) { this.bannerDescriptionContains = v; }

    public String getBannerActionContains() { return bannerActionContains; }
    public void setBannerActionContains(String v) { this.bannerActionContains = v; }

    public String getBuildLineContains() { return buildLineContains; }
    public void setBuildLineContains(String buildLineContains) { this.buildLineContains = buildLineContains; }

    public int getHttpStatus() { return httpStatus; }
    public void setHttpStatus(int httpStatus) { this.httpStatus = httpStatus; }

    public String getExceptionClass() { return exceptionClass; }
    public void setExceptionClass(String exceptionClass) { this.exceptionClass = exceptionClass; }
}
