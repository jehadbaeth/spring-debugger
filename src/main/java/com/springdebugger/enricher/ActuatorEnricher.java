package com.springdebugger.enricher;

import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.model.RawSignal;

import java.util.Optional;

/**
 * Actuator enrichment (M9). When a runtime error leaves the application still alive and it
 * exposes Spring Boot Actuator, this confirms the diagnosis against live state from
 * /actuator/health. It is strictly additive: it only sharpens an uncertain runtime card,
 * never weakens or blocks the offline result.
 *
 * <p>Trigger surface is deliberately narrow and documented: it runs only for non-HIGH cards
 * (the pipeline gate) at RUNTIME phase (a startup failure means there is no running app to
 * query), and only when {@link EnrichmentContext#httpGet} actually reaches the app.
 */
public final class ActuatorEnricher implements Enricher {

    @Override
    public DiagnosisCard enrich(DiagnosisCard card, RawSignal signal, EnrichmentContext context) {
        if (card == null || context == null) return card;
        if (signal.getPhase() != Phase.RUNTIME) return card;

        Optional<String> health = safeGet(context, "/actuator/health");
        if (health.isEmpty()) return card;

        Optional<String> status = ActuatorReader.overallHealth(health.get());
        if (status.isEmpty() || "UP".equals(status.get())) return card;

        String component = ActuatorReader.firstDownComponent(health.get()).orElse("a dependency");
        String live = " Live /actuator/health reports " + status.get()
                + " with '" + component + "' down.";

        return new DiagnosisCard(
                card.getRuleId(),
                card.getPhase(),
                card.getDiagnosisSentence() + live,
                card.getFixSentence(),
                Confidence.HIGH,
                card.getExcerpt());
    }

    private Optional<String> safeGet(EnrichmentContext context, String path) {
        try {
            return context.httpGet(path);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
