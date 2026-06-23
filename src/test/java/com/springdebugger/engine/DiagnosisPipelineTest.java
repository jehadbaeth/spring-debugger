package com.springdebugger.engine;

import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.model.RawSignal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the pipeline control flow with stubbed engines, no live IDE required:
 * a rule hit short-circuits the LLM; a miss falls back to the LLM; with no LLM
 * configured a miss returns empty.
 */
class DiagnosisPipelineTest {

    private static RawSignal anySignal() {
        return new RawSignal(Phase.RUNTIME, null, null, null, null, null, -1, List.of(), "excerpt");
    }

    private static DiagnosisCard card(String ruleId, Confidence confidence) {
        return new DiagnosisCard(ruleId, Phase.RUNTIME, "diagnosis", "fix", confidence, "excerpt");
    }

    @Test
    void ruleHitIsReturnedAndLlmIsNotConsulted() {
        boolean[] llmCalled = {false};
        DiagnosisEngine rule = s -> Optional.of(card("2.1", Confidence.HIGH));
        DiagnosisEngine llm = s -> { llmCalled[0] = true; return Optional.of(card("llm", Confidence.MEDIUM)); };

        DiagnosisPipeline pipeline = new DiagnosisPipeline(rule, List.of(), llm);
        Optional<DiagnosisCard> result = pipeline.run(anySignal());

        assertThat(result).isPresent();
        assertThat(result.get().getRuleId()).isEqualTo("2.1");
        assertThat(llmCalled[0]).isFalse();
    }

    @Test
    void ruleMissFallsBackToLlm() {
        DiagnosisEngine rule = s -> Optional.empty();
        DiagnosisEngine llm = s -> Optional.of(card("llm", Confidence.MEDIUM));

        DiagnosisPipeline pipeline = new DiagnosisPipeline(rule, List.of(), llm);
        Optional<DiagnosisCard> result = pipeline.run(anySignal());

        assertThat(result).isPresent();
        assertThat(result.get().getRuleId()).isEqualTo("llm");
    }

    @Test
    void ruleMissWithoutLlmReturnsEmpty() {
        DiagnosisEngine rule = s -> Optional.empty();

        DiagnosisPipeline pipeline = new DiagnosisPipeline(rule, List.of(), null);
        Optional<DiagnosisCard> result = pipeline.run(anySignal());

        assertThat(result).isEmpty();
    }
}
