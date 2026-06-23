package com.springdebugger.enricher;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorReaderTest {

    @Test
    void readsOverallStatus() {
        String body = "{\"status\":\"DOWN\",\"components\":{\"db\":{\"status\":\"UP\"}}}";
        assertThat(ActuatorReader.overallHealth(body)).contains("DOWN");
    }

    @Test
    void findsFirstDownComponent() {
        String body = "{\"status\":\"DOWN\",\"components\":{"
                + "\"diskSpace\":{\"status\":\"UP\"},"
                + "\"db\":{\"status\":\"DOWN\",\"details\":{\"error\":\"connection refused\"}}}}";
        assertThat(ActuatorReader.firstDownComponent(body)).contains("db");
    }

    @Test
    void noDownComponentWhenAllUp() {
        String body = "{\"status\":\"UP\",\"components\":{\"db\":{\"status\":\"UP\"}}}";
        assertThat(ActuatorReader.firstDownComponent(body)).isEmpty();
    }

    @Test
    void readsEffectivePropertySource() {
        String body = "{\"property\":{\"source\":\"systemEnvironment\",\"value\":\"prod\"},"
                + "\"propertySources\":[{\"name\":\"systemEnvironment\",\"property\":{\"value\":\"prod\"}}]}";
        assertThat(ActuatorReader.effectivePropertySource(body)).contains("systemEnvironment");
    }

    @Test
    void nullBodiesAreEmpty() {
        assertThat(ActuatorReader.overallHealth(null)).isEmpty();
        assertThat(ActuatorReader.firstDownComponent(null)).isEmpty();
        assertThat(ActuatorReader.effectivePropertySource(null)).isEmpty();
    }
}
