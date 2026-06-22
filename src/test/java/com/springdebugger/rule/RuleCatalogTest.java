package com.springdebugger.rule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleCatalogTest {

    @Test
    void loadsAllRules() {
        RuleCatalog catalog = RuleCatalog.load();
        assertThat(catalog.size()).isGreaterThan(0);
    }

    @Test
    void everyRuleHasRequiredFields() {
        RuleCatalog catalog = RuleCatalog.load();
        for (Rule rule : catalog.all()) {
            assertThat(rule.getId()).as("id must be set").isNotBlank();
            assertThat(rule.getName()).as("name must be set for rule " + rule.getId()).isNotBlank();
            assertThat(rule.getPhases()).as("phases must be set for rule " + rule.getId()).isNotEmpty();
            assertThat(rule.getSignals()).as("signals must be set for rule " + rule.getId()).isNotNull();
            assertThat(rule.getDiagnosis()).as("diagnosis must be set for rule " + rule.getId()).isNotBlank();
            assertThat(rule.getFix()).as("fix must be set for rule " + rule.getId()).isNotBlank();
            assertThat(rule.getConfidence()).as("confidence must be set for rule " + rule.getId()).isNotNull();
            assertThat(rule.getStatus()).as("status must be set for rule " + rule.getId()).isNotBlank();
        }
    }

    @Test
    void lookupByIdWorks() {
        RuleCatalog catalog = RuleCatalog.load();
        Rule rule = catalog.byId("2.1");
        assertThat(rule).isNotNull();
        assertThat(rule.getName()).isEqualTo("NoSuchBeanDefinitionException");
    }
}
