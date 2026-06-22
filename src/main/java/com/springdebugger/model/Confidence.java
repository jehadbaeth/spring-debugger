package com.springdebugger.model;

public enum Confidence {
    /** Unambiguous signal, e.g. a FailureAnalyzer banner match. */
    HIGH,
    /** Heuristic match on exception class alone; may need enrichment. */
    MEDIUM,
    /** Weak signal; shown only in debug mode. */
    LOW,
    /** No rule fired; LLM fallback trigger when that mode is enabled. */
    NONE
}
