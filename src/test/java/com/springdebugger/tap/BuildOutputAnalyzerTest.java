package com.springdebugger.tap;

import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.rule.RuleCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the pure build-output analysis core. This is the part of M6 that can be
 * verified automatically: given canned build output it must (a) stay silent on generic
 * Java errors and (b) produce the right diagnosis when a Spring-specific compile marker
 * is present. The live tap firing in a running IDE is a separate manual check.
 */
class BuildOutputAnalyzerTest {

    private static BuildOutputAnalyzer analyzer;

    @BeforeAll
    static void setup() {
        analyzer = new BuildOutputAnalyzer(RuleCatalog.load());
    }

    @Test
    void mapStructUnmappedTargetProducesRule13_1() {
        Optional<DiagnosisCard> card = analyzer.analyze(loadFixture("fixtures/13.1-unmapped-target.log"));
        assertThat(card).isPresent();
        assertThat(card.get().getRuleId()).isEqualTo("13.1");
    }

    @Test
    void webSecurityConfigurerAdapterProducesRule6_4() {
        Optional<DiagnosisCard> card = analyzer.analyze(loadFixture("fixtures/6.4-web-security-configurer.log"));
        assertThat(card).isPresent();
        assertThat(card.get().getRuleId()).isEqualTo("6.4");
    }

    @Test
    void genericCompileErrorIsIgnored() {
        // A plain typo with no Spring marker must not produce a diagnosis: that is IntelliJ's job.
        String generic = "/src/main/java/com/example/Foo.java:10: error: cannot find symbol\n"
                + "        bar.doThing();\n"
                + "  symbol:   method doThing()\n"
                + "1 error\nBUILD FAILED";
        assertThat(analyzer.analyze(generic)).isEmpty();
    }

    @Test
    void emptyAndBlankInputAreIgnored() {
        assertThat(analyzer.analyze(null)).isEmpty();
        assertThat(analyzer.analyze("   ")).isEmpty();
    }

    @Test
    void springMarkerGateRecognisesKnownMarkers() {
        assertThat(BuildOutputAnalyzer.isSpringRelated("error: Unmapped target property: \"x\"")).isTrue();
        assertThat(BuildOutputAnalyzer.isSpringRelated("cannot find symbol WebSecurityConfigurerAdapter")).isTrue();
        assertThat(BuildOutputAnalyzer.isSpringRelated("error: cannot find symbol doThing()")).isFalse();
    }

    private String loadFixture(String fixturePath) {
        try (InputStream is = BuildOutputAnalyzerTest.class.getResourceAsStream("/" + fixturePath)) {
            Objects.requireNonNull(is, "Fixture not found: " + fixturePath);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
