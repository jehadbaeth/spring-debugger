package com.springdebugger.enricher;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.model.RawSignal;

import java.util.List;
import java.util.Optional;

/**
 * Exercises the PSI enrichment against a REAL in-memory PSI index (not stubbed ClassFacts).
 * This is the headless equivalent of confirming M8 in a running IDE: it proves the
 * IdeEnrichmentContext actually resolves classes, reads annotations, and finds the
 * @SpringBootApplication scan root from the platform's index.
 */
public class PsiEnrichmentIntegrationTest extends LightJavaCodeInsightFixtureTestCase {

    private void addSpringStubs() {
        myFixture.addClass("package org.springframework.boot.autoconfigure; "
                + "public @interface SpringBootApplication {}");
        myFixture.addClass("package org.springframework.stereotype; public @interface Service {}");
        myFixture.addClass("package com.example; "
                + "@org.springframework.boot.autoconfigure.SpringBootApplication public class App {}");
    }

    public void testFindsClassAndReadsNoStereotype() {
        addSpringStubs();
        myFixture.addClass("package com.example; public class OrderService {}");

        IdeEnrichmentContext ctx = new IdeEnrichmentContext(getProject());
        Optional<ClassFacts> facts = ctx.findClass("com.example.OrderService");

        assertTrue("class should resolve from the real index", facts.isPresent());
        assertFalse("OrderService has no stereotype", facts.get().hasStereotype());
        assertEquals("com.example", facts.get().packageName());
    }

    public void testReadsStereotypeAnnotation() {
        addSpringStubs();
        myFixture.addClass("package com.example; "
                + "@org.springframework.stereotype.Service public class PaymentService {}");

        Optional<ClassFacts> facts = new IdeEnrichmentContext(getProject()).findClass("PaymentService");
        assertTrue(facts.isPresent());
        assertTrue("@Service is a stereotype", facts.get().hasStereotype());
    }

    public void testFindsSpringBootApplicationPackage() {
        addSpringStubs();

        List<String> roots = new IdeEnrichmentContext(getProject()).springBootApplicationPackages();
        assertTrue("scan root should be discovered from @SpringBootApplication",
                roots.contains("com.example"));
    }

    public void testEnricherUpgradesMissingStereotypeAgainstRealPsi() {
        addSpringStubs();
        myFixture.addClass("package com.example; public class OrderService {}");

        RawSignal signal = new RawSignal(Phase.STARTUP,
                "org.springframework.beans.factory.NoSuchBeanDefinitionException",
                "No qualifying bean of type 'com.example.OrderService' available",
                null, null, null, -1,
                List.of("No qualifying bean of type 'com.example.OrderService' available"),
                "excerpt");
        DiagnosisCard offline = new DiagnosisCard("2.1", Phase.STARTUP,
                "offline diagnosis", "offline fix", Confidence.MEDIUM, "excerpt");

        DiagnosisCard enriched = new PsiEnricher()
                .enrich(offline, signal, new IdeEnrichmentContext(getProject()));

        assertEquals(Confidence.HIGH, enriched.getConfidence());
        assertTrue(enriched.getDiagnosisSentence().contains("no Spring stereotype"));
    }
}
