package com.springdebugger.enricher;

import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.model.RawSignal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the pure PSI enrichment logic, driven by a stubbed EnrichmentContext.
 * No live IDE or PSI index is needed: the context returns canned ClassFacts.
 */
class PsiEnricherTest {

    private final PsiEnricher enricher = new PsiEnricher();

    /** Stub context: a fixed class table plus the component-scan roots. */
    private static EnrichmentContext context(Map<String, ClassFacts> table, List<String> roots) {
        return new EnrichmentContext() {
            @Override public Optional<ClassFacts> findClass(String name) {
                String simple = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
                ClassFacts f = table.get(name);
                if (f == null) f = table.get(simple);
                return Optional.ofNullable(f);
            }
            @Override public List<String> springBootApplicationPackages() { return roots; }
            @Override public Optional<String> httpGet(String url) { return Optional.empty(); }
        };
    }

    private static RawSignal signalWithCause(String message) {
        return new RawSignal(Phase.STARTUP,
                "org.springframework.beans.factory.NoSuchBeanDefinitionException",
                message, null, null, null, -1, List.of(message), message);
    }

    private static DiagnosisCard mediumCard(String ruleId) {
        return new DiagnosisCard(ruleId, Phase.STARTUP, "offline diagnosis", "offline fix",
                Confidence.MEDIUM, "excerpt");
    }

    @Test
    void mapperInterfaceUpgradesRule13_4ToHigh() {
        var facts = new ClassFacts("com.example.UserMapper", "com.example", true,
                Set.of("Mapper"), false);
        var ctx = context(Map.of("com.example.UserMapper", facts), List.of("com.example"));

        DiagnosisCard out = enricher.enrich(mediumCard("13.4"),
                signalWithCause("No qualifying bean of type 'com.example.UserMapper' available"), ctx);

        assertThat(out.getConfidence()).isEqualTo(Confidence.HIGH);
        assertThat(out.getDiagnosisSentence()).contains("UserMapper").contains("@Mapper");
        assertThat(out.getFixSentence()).contains("componentModel");
    }

    @Test
    void missingStereotypeSharpensDiRule() {
        var facts = new ClassFacts("com.example.OrderService", "com.example", false,
                Set.of(), true);
        var ctx = context(Map.of("OrderService", facts), List.of("com.example"));

        DiagnosisCard out = enricher.enrich(mediumCard("2.3"),
                signalWithCause("required a bean of type 'com.example.OrderService' that could not be found"), ctx);

        assertThat(out.getConfidence()).isEqualTo(Confidence.HIGH);
        assertThat(out.getDiagnosisSentence()).contains("no Spring stereotype");
    }

    @Test
    void annotatedClassOutsideScanTreeReportsComponentScan() {
        var facts = new ClassFacts("com.other.PaymentService", "com.other", false,
                Set.of("Service"), true);
        var ctx = context(Map.of("PaymentService", facts), List.of("com.example"));

        DiagnosisCard out = enricher.enrich(mediumCard("2.3"),
                signalWithCause("required a bean of type 'com.other.PaymentService' that could not be found"), ctx);

        assertThat(out.getConfidence()).isEqualTo(Confidence.HIGH);
        assertThat(out.getDiagnosisSentence()).contains("outside the package tree");
    }

    @Test
    void unknownClassLeavesCardUnchanged() {
        var ctx = context(Map.of(), List.of("com.example"));
        DiagnosisCard in = mediumCard("2.3");

        DiagnosisCard out = enricher.enrich(in,
                signalWithCause("required a bean of type 'com.example.Ghost' that could not be found"), ctx);

        assertThat(out).isSameAs(in);
    }

    @Test
    void nonTargetRuleIsLeftUnchanged() {
        var facts = new ClassFacts("com.example.Foo", "com.example", false, Set.of(), true);
        var ctx = context(Map.of("Foo", facts), List.of("com.example"));
        DiagnosisCard in = mediumCard("4.8");

        DiagnosisCard out = enricher.enrich(in, signalWithCause("'com.example.Foo'"), ctx);

        assertThat(out).isSameAs(in);
    }

    @Test
    void annotatedClassInsideScanTreeIsNotFlagged() {
        // Stereotype present and inside the scan root: the enricher has nothing to add.
        var facts = new ClassFacts("com.example.web.Ok", "com.example.web", false,
                Set.of("Service"), true);
        var ctx = context(Map.of("Ok", facts), List.of("com.example"));
        DiagnosisCard in = mediumCard("2.3");

        DiagnosisCard out = enricher.enrich(in, signalWithCause("'com.example.web.Ok'"), ctx);

        assertThat(out).isSameAs(in);
    }
}
