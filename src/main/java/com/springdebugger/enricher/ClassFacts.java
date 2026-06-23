package com.springdebugger.enricher;

import java.util.Set;

/**
 * Structural facts about a Java class, resolved once from the IDE's PSI index and
 * then handed to the enrichers as plain data. Keeping the facts as a record lets the
 * enricher decision logic be unit-tested with canned values instead of a live IDE.
 *
 * @param qualifiedName   fully qualified class name, e.g. com.example.web.OrderService
 * @param packageName     the package the class lives in, e.g. com.example.web
 * @param isInterface     true if the type is an interface
 * @param annotations     simple names of annotations on the type, e.g. {"Service", "Mapper"}
 * @param hasNoArgCtor    true if the class declares (or implicitly has) a no-argument constructor
 * @param inProjectSource true if the type lives in the user's editable source (false for a
 *                        third-party/library type, which cannot be annotated and needs an @Bean)
 * @param fileName        the source file name, e.g. OrderService.java, or null if unknown
 */
public record ClassFacts(
        String qualifiedName,
        String packageName,
        boolean isInterface,
        Set<String> annotations,
        boolean hasNoArgCtor,
        boolean inProjectSource,
        String fileName) {

    public boolean hasAnyAnnotation(String... simpleNames) {
        for (String name : simpleNames) {
            if (annotations.contains(name)) return true;
        }
        return false;
    }

    /** True if the type carries any Spring stereotype that makes it visible to component scanning. */
    public boolean hasStereotype() {
        return hasAnyAnnotation(
                "Component", "Service", "Repository", "Controller", "RestController",
                "Configuration", "Mapper");
    }
}
