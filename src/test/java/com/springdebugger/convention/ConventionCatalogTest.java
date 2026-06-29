package com.springdebugger.convention;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates conventions.yaml: it loads, ids are unique, every checkType resolves to a registered
 * implementation, and the seed Javadoc rule is shaped as documented. No fixed rule count is asserted
 * so adding rules never breaks this test.
 */
class ConventionCatalogTest {

    private final ConventionCatalog catalog = ConventionCatalog.load();

    @Test
    void catalogLoadsAtLeastOneRule() {
        assertThat(catalog.all()).isNotEmpty();
    }

    @Test
    void ruleIdsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (ConventionRule rule : catalog.all()) {
            assertThat(seen.add(rule.getId()))
                    .as("duplicate rule id: %s", rule.getId())
                    .isTrue();
        }
    }

    @Test
    void everyCheckTypeResolves() {
        for (ConventionRule rule : catalog.all()) {
            assertThat(CheckRegistry.has(rule.getCheckType()))
                    .as("rule %s references unknown checkType '%s'", rule.getId(), rule.getCheckType())
                    .isTrue();
        }
    }

    @Test
    void javadocRuleIsShapedAsDocumented() {
        ConventionRule rule = catalog.byId("JAVADOC_METHOD");
        assertThat(rule).isNotNull();
        assertThat(rule.getCheckType()).isEqualTo("javadocRequired");
        assertThat(rule.isEnabled()).isTrue();
        assertThat(rule.getSeverity()).isEqualTo("WARNING");
        assertThat(rule.appliesToFileType("java")).isTrue();
        assertThat(rule.appliesToFileType("robot")).isFalse();
        assertThat(rule.getMessage()).contains("{{method}}");
        assertThat(rule.getStatus()).isEqualTo("DONE");
    }
}
