package com.springdebugger.engine;

import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.RawSignal;

import java.util.Optional;

/**
 * Common interface for all diagnosis backends.
 * The offline rule engine and the future LLM adapter both implement this.
 */
public interface DiagnosisEngine {

    /**
     * Attempts to classify the signal and produce a diagnosis card.
     * Returns empty when the engine has no answer (confidence == NONE).
     */
    Optional<DiagnosisCard> diagnose(RawSignal signal);
}
