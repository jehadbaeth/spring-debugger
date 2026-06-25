// Port of com.springdebugger.enricher.ActuatorReader. Pure parsing of Spring Boot Actuator JSON,
// hand-rolled (the responses are shallow and well known) so it is unit tested with canned bodies.

const STATUS = /"status"\s*:\s*"([A-Z_]+)"/;

/** From an /actuator/health body, the overall status (the first status field is the aggregate). */
export function overallHealth(healthJson: string | null): string | null {
  if (healthJson === null) return null;
  const m = STATUS.exec(healthJson);
  return m ? m[1] : null;
}

/** From an /actuator/health body, the name of the first component reporting DOWN, or null. */
export function firstDownComponent(healthJson: string | null): string | null {
  if (healthJson === null) return null;
  const components = healthJson.indexOf('"components"');
  if (components < 0) return null;
  const m = /"([\w.-]+)"\s*:\s*\{[^{}]*"status"\s*:\s*"DOWN"/.exec(healthJson.substring(components));
  return m ? m[1] : null;
}

/** From an /actuator/env/{key} body, the property source supplying the effective value, or null. */
export function effectivePropertySource(envJson: string | null): string | null {
  if (envJson === null) return null;
  const m = /"name"\s*:\s*"([^"]+)"\s*,\s*"property"/.exec(envJson);
  return m ? m[1] : null;
}
