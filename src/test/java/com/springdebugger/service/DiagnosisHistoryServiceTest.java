package com.springdebugger.service;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;

/**
 * Verifies history grouping: repeats of the same diagnosis collapse to one row with a bumped
 * count, while distinct diagnoses each get their own row. This is what keeps a noisy
 * integration run (the same broken endpoint hit many times) from flooding the panel.
 */
public class DiagnosisHistoryServiceTest extends BasePlatformTestCase {

    private DiagnosisCard card(String ruleId, String diagnosis) {
        return new DiagnosisCard(ruleId, Phase.RUNTIME, diagnosis, "fix", Confidence.HIGH, "excerpt");
    }

    public void testRepeatsAreGroupedWithACount() {
        DiagnosisHistoryService history = DiagnosisHistoryService.getInstance(getProject());
        history.clear();

        DiagnosisCard c = card("5.1", "No handler for GET /api/x");
        history.addDiagnosis(c);
        history.addDiagnosis(card("5.1", "No handler for GET /api/x"));
        history.addDiagnosis(card("5.1", "No handler for GET /api/x"));

        assertEquals("repeats collapse to one row", 1, history.getHistory().size());
        assertEquals("count bumps per occurrence", 3, history.getOccurrences(c));
    }

    public void testDistinctDiagnosesGetSeparateRows() {
        DiagnosisHistoryService history = DiagnosisHistoryService.getInstance(getProject());
        history.clear();

        history.addDiagnosis(card("5.1", "No handler for GET /api/x"));
        history.addDiagnosis(card("4.13", "Unique constraint violated"));
        history.addDiagnosis(card("14.1", "Kafka broker unreachable"));

        assertEquals(3, history.getHistory().size());
    }
}
