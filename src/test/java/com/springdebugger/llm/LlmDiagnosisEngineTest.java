package com.springdebugger.llm;

import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.model.RawSignal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LlmDiagnosisEngineTest {

    private static RawSignal signal() {
        return new RawSignal(Phase.STARTUP, "com.example.WeirdException", "something odd",
                null, null, null, -1, List.of("something odd"), "stack trace excerpt");
    }

    private static LlmDiagnosisEngine engine(Optional<String> reply) {
        return new LlmDiagnosisEngine(prompt -> reply);
    }

    @Test
    void validJsonReplyBecomesMediumLlmCard() {
        var out = engine(Optional.of("{\"diagnosis\":\"The thing broke.\",\"fix\":\"Unbreak it.\"}"))
                .diagnose(signal());

        assertThat(out).isPresent();
        DiagnosisCard card = out.get();
        assertThat(card.getRuleId()).isEqualTo("llm");
        assertThat(card.getConfidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(card.getDiagnosisSentence()).isEqualTo("The thing broke.");
        assertThat(card.getFixSentence()).isEqualTo("Unbreak it.");
    }

    @Test
    void garbageReplyShowsNothing() {
        assertThat(engine(Optional.of("this is not json at all")).diagnose(signal())).isEmpty();
    }

    @Test
    void truncatedJsonShowsNothing() {
        assertThat(engine(Optional.of("{\"diagnosis\":\"half a sen")).diagnose(signal())).isEmpty();
    }

    @Test
    void missingFixFieldShowsNothing() {
        assertThat(engine(Optional.of("{\"diagnosis\":\"only diagnosis\"}")).diagnose(signal())).isEmpty();
    }

    @Test
    void blankFieldsShowNothing() {
        assertThat(engine(Optional.of("{\"diagnosis\":\"  \",\"fix\":\"x\"}")).diagnose(signal())).isEmpty();
    }

    @Test
    void emptyReplyShowsNothing() {
        assertThat(engine(Optional.empty()).diagnose(signal())).isEmpty();
    }

    @Test
    void promptIncludesKeySignalFields() {
        String prompt = LlmDiagnosisEngine.buildPrompt(signal());
        assertThat(prompt).contains("com.example.WeirdException")
                .contains("something odd")
                .contains("STARTUP")
                .contains("\"diagnosis\"").contains("\"fix\"");
    }
}
