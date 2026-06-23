package com.springdebugger.enricher;

import java.util.List;
import java.util.Optional;

/**
 * The thin adapter through which enrichers reach the IDE and the running application.
 * The IDE-backed implementation lives behind this interface so the enricher decision
 * logic can be unit-tested with stubbed facts (no live PSI index, no HTTP).
 */
public interface EnrichmentContext {

    /**
     * Resolves structural facts about a class by simple or fully qualified name.
     * Returns empty when the class is not on the project index.
     */
    Optional<ClassFacts> findClass(String name);

    /** Packages that host an @SpringBootApplication main class (the component-scan roots). */
    List<String> springBootApplicationPackages();

    /**
     * Performs an HTTP GET against a running application endpoint (Actuator).
     * Returns empty when the app is not running, the port is unknown, or the call fails.
     */
    Optional<String> httpGet(String url);
}
