package com.springdebugger.enricher;

import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.RawSignal;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PSI-backed enrichment (M8). Confirms a structural claim against the project's actual
 * source so an uncertain rule match can be sharpened or upgraded. All IDE access happens
 * behind {@link EnrichmentContext}; this class is pure decision logic and is unit-tested
 * with canned {@link ClassFacts}.
 *
 * <p>Targeted cases:
 * <ul>
 *   <li>MapStruct (13.3 / 13.4): if the missing bean type is a {@code @Mapper} interface,
 *       confirm it is a MapStruct mapper missing from the context and upgrade to HIGH.</li>
 *   <li>Dependency injection (2.x): if the missing bean type resolves to a class that has
 *       no Spring stereotype, or lives outside the component-scan tree, say so precisely.</li>
 * </ul>
 *
 * On any uncertainty it returns the input card unchanged, never weakening the offline result.
 */
public final class PsiEnricher implements Enricher {

    /** A single-quoted fully-qualified or simple type name, e.g. 'com.example.UserMapper'. */
    private static final Pattern QUOTED_TYPE = Pattern.compile("'([\\w.$]+)'");
    /** Bare "of type com.example.Foo" form. */
    private static final Pattern OF_TYPE = Pattern.compile("of type \\[?([\\w.$]+)");

    @Override
    public DiagnosisCard enrich(DiagnosisCard card, RawSignal signal, EnrichmentContext context) {
        if (card == null || context == null) return card;
        String ruleId = card.getRuleId();
        if (ruleId == null) return card;

        boolean diRule = ruleId.startsWith("2.") || ruleId.equals("1.6") || ruleId.equals("1.7");
        boolean mapStructRule = ruleId.equals("13.3") || ruleId.equals("13.4");
        if (!diRule && !mapStructRule) return card;

        String typeName = extractTypeName(signal);
        if (typeName == null) return card;

        Optional<ClassFacts> found = safeFindClass(context, typeName);
        if (found.isEmpty()) return card;
        ClassFacts facts = found.get();

        if (facts.isInterface() && facts.hasAnyAnnotation("Mapper")) {
            return new DiagnosisCard(
                    card.getRuleId(),
                    card.getPhase(),
                    "The MapStruct mapper interface " + simple(facts.qualifiedName())
                            + " is annotated @Mapper but no implementation bean is registered in the Spring context.",
                    "Add componentModel = \"spring\" to the @Mapper annotation so the generated implementation is "
                            + "registered as a bean, and confirm the mapstruct-processor runs during the build.",
                    Confidence.HIGH,
                    card.getExcerpt());
        }

        if (diRule && !facts.isInterface() && !facts.hasStereotype()) {
            return new DiagnosisCard(
                    card.getRuleId(),
                    card.getPhase(),
                    "The class " + simple(facts.qualifiedName())
                            + " exists in the project but carries no Spring stereotype, so component scanning never registers it as a bean.",
                    "Annotate " + simple(facts.qualifiedName())
                            + " with @Component, @Service, @Repository, or @Controller, or declare it as an @Bean in a @Configuration class.",
                    Confidence.HIGH,
                    card.getExcerpt());
        }

        if (diRule && facts.hasStereotype() && outsideScanTree(facts, context)) {
            return new DiagnosisCard(
                    card.getRuleId(),
                    card.getPhase(),
                    "The class " + simple(facts.qualifiedName()) + " is annotated but lives in package "
                            + facts.packageName() + ", which is outside the package tree scanned by @SpringBootApplication.",
                    "Move your @SpringBootApplication main class up to a parent package that contains "
                            + facts.packageName() + ", or add @ComponentScan(\"" + facts.packageName() + "\").",
                    Confidence.HIGH,
                    card.getExcerpt());
        }

        return card;
    }

    private boolean outsideScanTree(ClassFacts facts, EnrichmentContext context) {
        List<String> roots = context.springBootApplicationPackages();
        if (roots == null || roots.isEmpty()) return false;
        String pkg = facts.packageName() == null ? "" : facts.packageName();
        for (String root : roots) {
            if (pkg.equals(root) || pkg.startsWith(root + ".")) return false;
        }
        return true;
    }

    private Optional<ClassFacts> safeFindClass(EnrichmentContext context, String typeName) {
        try {
            return context.findClass(typeName);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Pulls the bean/type name from the signal: quoted type, "of type X", or the failing bean name. */
    String extractTypeName(RawSignal signal) {
        String message = signal.getDeepestCausedByMessage();
        if (message != null) {
            Matcher q = QUOTED_TYPE.matcher(message);
            while (q.find()) {
                String candidate = q.group(1);
                if (looksLikeType(candidate)) return candidate;
            }
            Matcher o = OF_TYPE.matcher(message);
            if (o.find() && looksLikeType(o.group(1))) return o.group(1);
        }
        String bean = signal.getFailingBeanName();
        return (bean != null && !bean.isBlank()) ? bean : null;
    }

    private boolean looksLikeType(String s) {
        if (s == null || s.isEmpty()) return false;
        // A class name either is qualified (has a dot) or starts with an upper-case letter.
        return s.contains(".") || Character.isUpperCase(s.charAt(0));
    }

    private String simple(String qualified) {
        if (qualified == null) return "the type";
        int dot = qualified.lastIndexOf('.');
        return dot >= 0 ? qualified.substring(dot + 1) : qualified;
    }
}
