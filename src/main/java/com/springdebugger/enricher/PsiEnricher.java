package com.springdebugger.enricher;

import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.RawSignal;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PSI-backed enrichment (M8). Resolves the missing bean type against the project's actual
 * source and rewrites the diagnosis and fix to name the exact type, where it lives, which
 * annotation fits its role, and which bean needed it. All IDE access happens behind
 * {@link EnrichmentContext}; this class is pure decision logic, unit-tested with canned
 * {@link ClassFacts}. On any uncertainty it returns the input card unchanged.
 *
 * <p>It distinguishes the cases that change the advice:
 * <ul>
 *   <li>MapStruct {@code @Mapper} interface -> add componentModel = "spring".</li>
 *   <li>Third-party/library type (cannot be annotated) -> declare an {@code @Bean} method.</li>
 *   <li>Project interface with no bean -> annotate the implementation, not the interface.</li>
 *   <li>Project class with no stereotype -> add the best-fit stereotype, in its file.</li>
 *   <li>Annotated class outside the scan tree -> widen the component scan.</li>
 * </ul>
 */
public final class PsiEnricher implements Enricher {

    private static final Pattern QUOTED_TYPE = Pattern.compile("'([\\w.$]+)'");
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

        String fqn = facts.qualifiedName() != null ? facts.qualifiedName() : typeName;
        String type = simple(fqn);
        String where = facts.fileName() != null ? facts.fileName()
                : (facts.packageName() != null && !facts.packageName().isEmpty()
                    ? "package " + facts.packageName() : "the project");
        String neededBy = neededByClause(signal);

        // MapStruct mapper interface
        if (facts.isInterface() && facts.hasAnyAnnotation("Mapper")) {
            return rewrite(card, Confidence.HIGH,
                    "The MapStruct mapper " + fqn + " is annotated @Mapper but its generated "
                            + "implementation is not a Spring bean." + neededBy,
                    "Add componentModel = \"spring\" to @Mapper on " + type + " (" + where + "): "
                            + "@Mapper(componentModel = \"spring\"). MapStruct then registers " + type
                            + "Impl as a bean you can inject.");
        }

        // Third-party / library type: cannot be annotated, must be an @Bean
        if (diRule && !facts.inProjectSource()) {
            return rewrite(card, card.getConfidence(),
                    "Spring needs a bean of type " + fqn + ", but that type comes from a library, "
                            + "so it has no Spring stereotype and cannot be given one." + neededBy,
                    "Declare it as an @Bean in a @Configuration class (you cannot annotate library "
                            + "code): @Bean " + type + " " + decapitalize(type) + "() { return new "
                            + type + "(...); }.");
        }

        // Project interface with no bean: the implementation must be the bean
        if (diRule && facts.isInterface() && !facts.hasStereotype()) {
            return rewrite(card, Confidence.HIGH,
                    "No bean of type " + fqn + " is registered. It is an interface, and Spring "
                            + "registers implementations, not the interface itself." + neededBy,
                    "Annotate the class that implements " + type + " with " + chooseStereotype(type)[0]
                            + " (or another stereotype matching its role), or declare an @Bean method "
                            + "returning " + type + " in a @Configuration class.");
        }

        // Project class with no stereotype: add the best-fit one, in its own file
        if (diRule && !facts.hasStereotype()) {
            String[] ann = chooseStereotype(type);
            return rewrite(card, Confidence.HIGH,
                    "The class " + fqn + " exists in " + where + " but has no Spring stereotype, so "
                            + "component scanning never registers it as a bean." + neededBy,
                    "Add " + ann[0] + " to " + type + " in " + where + " (" + ann[1] + "), and keep "
                            + type + " inside the package tree under your @SpringBootApplication class "
                            + "so it is scanned.");
        }

        // Annotated but outside the component-scan tree
        if (diRule && facts.hasStereotype() && outsideScanTree(facts, context)) {
            return rewrite(card, Confidence.HIGH,
                    "The class " + fqn + " is annotated but lives in package " + facts.packageName()
                            + ", outside the package tree scanned by @SpringBootApplication." + neededBy,
                    "Move your @SpringBootApplication main class up to a parent package of "
                            + facts.packageName() + ", or add @ComponentScan(\"" + facts.packageName()
                            + "\") so " + type + " is discovered.");
        }

        return card;
    }

    private DiagnosisCard rewrite(DiagnosisCard card, Confidence confidence, String diagnosis, String fix) {
        return new DiagnosisCard(card.getRuleId(), card.getPhase(), diagnosis, fix, confidence, card.getExcerpt());
    }

    /** "@Repository"/"@Service"/"@RestController"/"@Component" plus a short rationale, by name. */
    static String[] chooseStereotype(String simpleName) {
        String n = simpleName.toLowerCase();
        if (n.endsWith("repository") || n.endsWith("dao") || n.endsWith("repo")) {
            return new String[]{"@Repository", "it is a data-access type"};
        }
        if (n.endsWith("service") || n.endsWith("serviceimpl") || n.endsWith("manager")) {
            return new String[]{"@Service", "it holds business logic"};
        }
        if (n.endsWith("controller") || n.endsWith("resource") || n.endsWith("endpoint")) {
            return new String[]{"@RestController", "it handles web requests"};
        }
        return new String[]{"@Component", "it is a general Spring-managed component"};
    }

    private String neededByClause(RawSignal signal) {
        String bean = signal.getFailingBeanName();
        return (bean != null && !bean.isBlank()) ? " It is required by bean '" + bean + "'." : "";
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
        return null;
    }

    private boolean looksLikeType(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.contains(".") || Character.isUpperCase(s.charAt(0));
    }

    private String simple(String qualified) {
        if (qualified == null) return "the type";
        int dot = qualified.lastIndexOf('.');
        return dot >= 0 ? qualified.substring(dot + 1) : qualified;
    }

    private static String decapitalize(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
