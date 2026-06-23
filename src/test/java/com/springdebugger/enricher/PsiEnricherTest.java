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

    private static ClassFacts facts(String fqn, boolean isInterface, Set<String> annotations,
                                    boolean inProjectSource) {
        String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
        String pkg = fqn.contains(".") ? fqn.substring(0, fqn.lastIndexOf('.')) : "";
        return new ClassFacts(fqn, pkg, isInterface, annotations, true, inProjectSource, simple + ".java");
    }

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

    private static RawSignal signal(String message, String failingBean) {
        return new RawSignal(Phase.STARTUP,
                "org.springframework.beans.factory.NoSuchBeanDefinitionException",
                message, null, null, failingBean, -1, List.of(message), message);
    }

    private static DiagnosisCard card(String ruleId) {
        return new DiagnosisCard(ruleId, Phase.STARTUP, "offline diagnosis", "offline fix",
                Confidence.HIGH, "excerpt");
    }

    @Test
    void mapperInterfaceNamesMapperAndComponentModel() {
        var ctx = context(Map.of("UserMapper",
                facts("com.example.UserMapper", true, Set.of("Mapper"), true)), List.of("com.example"));

        DiagnosisCard out = enricher.enrich(card("13.4"),
                signal("No qualifying bean of type 'com.example.UserMapper' available", null), ctx);

        assertThat(out.getDiagnosisSentence()).contains("com.example.UserMapper").contains("@Mapper");
        assertThat(out.getFixSentence()).contains("componentModel").contains("UserMapper.java");
    }

    @Test
    void missingStereotypeOnServicePicksServiceAndNamesFileAndConsumer() {
        var ctx = context(Map.of("OrderService",
                facts("com.example.OrderService", false, Set.of(), true)), List.of("com.example"));

        DiagnosisCard out = enricher.enrich(card("2.1"),
                signal("No qualifying bean of type 'com.example.OrderService' available", "checkoutController"), ctx);

        assertThat(out.getDiagnosisSentence())
                .contains("com.example.OrderService")
                .contains("no Spring stereotype")
                .contains("required by bean 'checkoutController'");
        assertThat(out.getFixSentence()).contains("@Service").contains("OrderService.java");
    }

    @Test
    void missingStereotypeOnRepositoryPicksRepository() {
        var ctx = context(Map.of("CustomerRepository",
                facts("com.example.CustomerRepository", false, Set.of(), true)), List.of("com.example"));

        DiagnosisCard out = enricher.enrich(card("2.1"),
                signal("No qualifying bean of type 'com.example.CustomerRepository' available", null), ctx);

        assertThat(out.getFixSentence()).contains("@Repository");
    }

    @Test
    void thirdPartyTypeRecommendsBeanMethod() {
        var ctx = context(Map.of("RestTemplate",
                facts("org.springframework.web.client.RestTemplate", false, Set.of(), false)), List.of("com.example"));

        DiagnosisCard out = enricher.enrich(card("2.1"),
                signal("No qualifying bean of type 'org.springframework.web.client.RestTemplate' available", null), ctx);

        assertThat(out.getDiagnosisSentence()).contains("library");
        assertThat(out.getFixSentence()).contains("@Bean").contains("restTemplate()");
    }

    @Test
    void projectInterfaceTellsUserToAnnotateImplementation() {
        var ctx = context(Map.of("PaymentGateway",
                facts("com.example.PaymentGateway", true, Set.of(), true)), List.of("com.example"));

        DiagnosisCard out = enricher.enrich(card("2.1"),
                signal("No qualifying bean of type 'com.example.PaymentGateway' available", null), ctx);

        assertThat(out.getDiagnosisSentence()).contains("interface");
        assertThat(out.getFixSentence()).contains("implements");
    }

    @Test
    void annotatedClassOutsideScanTreeReportsComponentScan() {
        var ctx = context(Map.of("PaymentService",
                facts("com.other.PaymentService", false, Set.of("Service"), true)), List.of("com.example"));

        DiagnosisCard out = enricher.enrich(card("2.1"),
                signal("No qualifying bean of type 'com.other.PaymentService' available", null), ctx);

        assertThat(out.getDiagnosisSentence()).contains("outside the package tree");
        assertThat(out.getFixSentence()).contains("@ComponentScan").contains("com.other");
    }

    @Test
    void unknownClassLeavesCardUnchanged() {
        var ctx = context(Map.of(), List.of("com.example"));
        DiagnosisCard in = card("2.1");
        DiagnosisCard out = enricher.enrich(in, signal("'com.example.Ghost' available", null), ctx);
        assertThat(out).isSameAs(in);
    }

    @Test
    void nonTargetRuleIsLeftUnchanged() {
        var ctx = context(Map.of("Foo", facts("com.example.Foo", false, Set.of(), true)), List.of("com.example"));
        DiagnosisCard in = card("4.8");
        assertThat(enricher.enrich(in, signal("'com.example.Foo'", null), ctx)).isSameAs(in);
    }

    @Test
    void annotatedClassInsideScanTreeIsNotFlagged() {
        var ctx = context(Map.of("Ok", facts("com.example.web.Ok", false, Set.of("Service"), true)),
                List.of("com.example"));
        DiagnosisCard in = card("2.1");
        assertThat(enricher.enrich(in, signal("'com.example.web.Ok'", null), ctx)).isSameAs(in);
    }
}
