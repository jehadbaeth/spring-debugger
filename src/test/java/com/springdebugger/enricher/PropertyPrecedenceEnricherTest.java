package com.springdebugger.enricher;

import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.model.RawSignal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyPrecedenceEnricherTest {

    private final PropertyPrecedenceEnricher enricher = new PropertyPrecedenceEnricher();

    private static EnrichmentContext httpStub(Function<String, Optional<String>> get) {
        return new EnrichmentContext() {
            @Override public Optional<ClassFacts> findClass(String name) { return Optional.empty(); }
            @Override public List<String> springBootApplicationPackages() { return List.of(); }
            @Override public Optional<String> httpGet(String url) { return get.apply(url); }
        };
    }

    private static RawSignal runtimeSignalMentioning(String key) {
        String msg = "Could not resolve placeholder '" + key + "' in value";
        return new RawSignal(Phase.RUNTIME, "java.lang.IllegalArgumentException", msg,
                null, null, null, -1, List.of(msg), msg);
    }

    private static DiagnosisCard card() {
        return new DiagnosisCard("3.1", Phase.RUNTIME, "A placeholder is unresolved.", "Define it.",
                Confidence.MEDIUM, "excerpt");
    }

    @Test
    void appendsEffectiveSourceWhenEnvKnowsTheKey() {
        var ctx = httpStub(url -> url.equals("/actuator/env/app.timeout")
                ? Optional.of("{\"property\":{\"source\":\"systemEnvironment\",\"value\":\"5s\"},"
                    + "\"propertySources\":[{\"name\":\"systemEnvironment\",\"property\":{\"value\":\"5s\"}}]}")
                : Optional.empty());

        DiagnosisCard out = enricher.enrich(card(), runtimeSignalMentioning("app.timeout"), ctx);

        assertThat(out.getDiagnosisSentence()).contains("app.timeout").contains("systemEnvironment");
    }

    @Test
    void noKeyLeavesCardUnchanged() {
        var ctx = httpStub(url -> Optional.empty());
        RawSignal noKey = new RawSignal(Phase.RUNTIME, "X", "nothing dotted here",
                null, null, null, -1, List.of(), "x");
        DiagnosisCard in = card();
        assertThat(enricher.enrich(in, noKey, ctx)).isSameAs(in);
    }

    @Test
    void unreachableEnvLeavesCardUnchanged() {
        var ctx = httpStub(url -> Optional.empty());
        DiagnosisCard in = card();
        assertThat(enricher.enrich(in, runtimeSignalMentioning("app.timeout"), ctx)).isSameAs(in);
    }

    @Test
    void startupPhaseIsNotProbed() {
        boolean[] called = {false};
        var ctx = httpStub(url -> { called[0] = true; return Optional.empty(); });
        RawSignal startup = new RawSignal(Phase.STARTUP, "X", "spring.datasource.url missing",
                null, null, null, -1, List.of(), "x");
        DiagnosisCard in = card();
        assertThat(enricher.enrich(in, startup, ctx)).isSameAs(in);
        assertThat(called[0]).isFalse();
    }
}
