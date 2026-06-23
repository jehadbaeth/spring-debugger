package com.springdebugger.enricher;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure parsing of Spring Boot Actuator JSON responses. Kept free of any HTTP or IDE code
 * so it can be unit-tested with canned response bodies. The actual fetching is done by the
 * {@link EnrichmentContext#httpGet} adapter; this class only interprets what comes back.
 *
 * <p>Intentionally a small hand-rolled parser rather than a full JSON dependency: we only
 * need a couple of fields and the responses are well known and shallow.
 */
public final class ActuatorReader {

    private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"([A-Z_]+)\"");

    private ActuatorReader() {}

    /**
     * From an /actuator/health body, returns the overall status (UP, DOWN, OUT_OF_SERVICE...).
     * The first status field in the body is the aggregate one.
     */
    public static Optional<String> overallHealth(String healthJson) {
        if (healthJson == null) return Optional.empty();
        Matcher m = STATUS.matcher(healthJson);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /**
     * From an /actuator/health body, returns the name of the first component reporting DOWN,
     * e.g. "db" or "redis". Empty when no component is down or components are not detailed.
     */
    public static Optional<String> firstDownComponent(String healthJson) {
        if (healthJson == null) return Optional.empty();
        int components = healthJson.indexOf("\"components\"");
        if (components < 0) return Optional.empty();
        // Match  "name": { ... "status":"DOWN"
        Matcher m = Pattern.compile("\"([\\w.-]+)\"\\s*:\\s*\\{[^{}]*\"status\"\\s*:\\s*\"DOWN\"")
                .matcher(healthJson.substring(components));
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /**
     * From an /actuator/env/{key} body, returns the property source that supplies the
     * effective value for the key, e.g. "systemEnvironment" or "Config resource ...".
     * Empty when the key is absent.
     */
    public static Optional<String> effectivePropertySource(String envJson) {
        if (envJson == null) return Optional.empty();
        // The first "source" key under "propertySources" wins (highest precedence first).
        Matcher m = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"property\"")
                .matcher(envJson);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
