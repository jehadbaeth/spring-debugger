package com.springdebugger.enricher;

import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.model.RawSignal;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Property-precedence enrichment (M9, completing the Actuator layer). When a runtime
 * configuration error references a property key and the app is still alive with Actuator
 * exposed, this reports which property source actually supplies the effective value via
 * /actuator/env/{key}. This is the consumer for {@link ActuatorReader#effectivePropertySource}.
 *
 * <p>Strictly additive: it appends a clause when it can confirm the source and otherwise
 * returns the card unchanged. Confidence is left as-is (this adds context, not certainty).
 */
public final class PropertyPrecedenceEnricher implements Enricher {

    /** A dotted, lower-case Spring property key such as spring.datasource.url or app.timeout. */
    private static final Pattern PROPERTY_KEY = Pattern.compile("[a-z][a-z0-9]*(?:\\.[a-z0-9-]+)+");

    @Override
    public DiagnosisCard enrich(DiagnosisCard card, RawSignal signal, EnrichmentContext context) {
        if (card == null || context == null) return card;
        if (signal.getPhase() != Phase.RUNTIME) return card;

        String key = firstPropertyKey(signal.getDeepestCausedByMessage());
        if (key == null) key = firstPropertyKey(card.getDiagnosisSentence());
        if (key == null) return card;

        Optional<String> env = safeGet(context, "/actuator/env/" + key);
        if (env.isEmpty()) return card;

        Optional<String> source = ActuatorReader.effectivePropertySource(env.get());
        if (source.isEmpty()) return card;

        return new DiagnosisCard(
                card.getRuleId(),
                card.getPhase(),
                card.getDiagnosisSentence()
                        + " The effective value of '" + key + "' comes from " + source.get() + ".",
                card.getFixSentence(),
                card.getConfidence(),
                card.getExcerpt());
    }

    static String firstPropertyKey(String text) {
        if (text == null) return null;
        Matcher m = PROPERTY_KEY.matcher(text);
        return m.find() ? m.group() : null;
    }

    private Optional<String> safeGet(EnrichmentContext context, String path) {
        try {
            return context.httpGet(path);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
