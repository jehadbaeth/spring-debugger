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

class ActuatorEnricherTest {

    private final ActuatorEnricher enricher = new ActuatorEnricher();

    private static EnrichmentContext httpStub(Function<String, Optional<String>> get) {
        return new EnrichmentContext() {
            @Override public Optional<ClassFacts> findClass(String name) { return Optional.empty(); }
            @Override public List<String> springBootApplicationPackages() { return List.of(); }
            @Override public Optional<String> httpGet(String url) { return get.apply(url); }
        };
    }

    private static RawSignal runtimeSignal() {
        return new RawSignal(Phase.RUNTIME, "org.example.SomeException", "boom",
                null, null, null, -1, List.of("boom"), "boom");
    }

    private static DiagnosisCard card() {
        return new DiagnosisCard("4.4", Phase.RUNTIME, "Pool exhausted.", "Fix the leak.",
                Confidence.MEDIUM, "excerpt");
    }

    @Test
    void downHealthSharpensAndUpgrades() {
        var ctx = httpStub(url -> Optional.of(
                "{\"status\":\"DOWN\",\"components\":{\"db\":{\"status\":\"DOWN\"}}}"));

        DiagnosisCard out = enricher.enrich(card(), runtimeSignal(), ctx);

        assertThat(out.getConfidence()).isEqualTo(Confidence.HIGH);
        assertThat(out.getDiagnosisSentence()).contains("DOWN").contains("db");
    }

    @Test
    void healthUpLeavesCardUnchanged() {
        var ctx = httpStub(url -> Optional.of("{\"status\":\"UP\"}"));
        DiagnosisCard in = card();
        assertThat(enricher.enrich(in, runtimeSignal(), ctx)).isSameAs(in);
    }

    @Test
    void unreachableAppLeavesCardUnchanged() {
        var ctx = httpStub(url -> Optional.empty());
        DiagnosisCard in = card();
        assertThat(enricher.enrich(in, runtimeSignal(), ctx)).isSameAs(in);
    }

    @Test
    void startupPhaseIsNotProbed() {
        boolean[] called = {false};
        var ctx = httpStub(url -> { called[0] = true; return Optional.empty(); });
        RawSignal startup = new RawSignal(Phase.STARTUP, null, null, null, null, null, -1,
                List.of(), "x");
        DiagnosisCard in = card();

        assertThat(enricher.enrich(in, startup, ctx)).isSameAs(in);
        assertThat(called[0]).isFalse();
    }
}
