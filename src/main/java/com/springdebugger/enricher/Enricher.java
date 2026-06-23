package com.springdebugger.enricher;

import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.RawSignal;

/**
 * A second-pass analyser that runs only for non-HIGH confidence rule matches.
 * An enricher may confirm a structural claim (PSI, M8) or query the running app
 * (Actuator, M9) and return either the same card, a card with upgraded confidence,
 * or a card with a sharper diagnosis. It must never throw; on any failure it returns
 * the input card unchanged so the offline result is preserved.
 */
public interface Enricher {

    DiagnosisCard enrich(DiagnosisCard card, RawSignal signal, EnrichmentContext context);
}
