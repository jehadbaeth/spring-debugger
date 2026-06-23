package com.springdebugger.tap;

import com.springdebugger.engine.DiagnosisPipeline;
import com.springdebugger.extractor.LogExtractor;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.model.RawSignal;
import com.springdebugger.rule.RuleCatalog;

import java.util.Optional;

/**
 * The pure, IDE-free core shared by both build taps: it decides whether a chunk of
 * build output looks Spring-specific, and if so runs it through the diagnosis pipeline
 * at COMPILE phase. Keeping this free of any IntelliJ build API means it is fully
 * unit-testable with canned build output, which is the part of M6 we can actually verify
 * automatically (the live tap firing must be checked in a running IDE).
 */
public final class BuildOutputAnalyzer {

    private final LogExtractor extractor = new LogExtractor();
    private final DiagnosisPipeline pipeline;

    public BuildOutputAnalyzer(RuleCatalog catalog) {
        this.pipeline = new DiagnosisPipeline(catalog);
    }

    public Optional<DiagnosisCard> analyze(String buildOutput) {
        if (buildOutput == null || buildOutput.isBlank()) return Optional.empty();
        if (!isSpringRelated(buildOutput)) return Optional.empty();
        RawSignal signal = extractor.extract(buildOutput, Phase.COMPILE);
        return pipeline.run(signal);
    }

    /**
     * Generic Java compile errors are IntelliJ's job, not ours. This gate keeps the build
     * taps quiet unless the output carries a marker for one of the Spring-specific compile
     * rules (sections 6.4, 10.x, 13.x). It is deliberately broad on the marker side and
     * lets the rule catalog make the precise call.
     */
    static boolean isSpringRelated(String text) {
        return containsAny(text,
                "WebSecurityConfigurerAdapter",
                "spring-boot-configuration-processor",
                "lombok", "Lombok",
                "mapstruct", "MapStruct", "MapperImpl",
                "Unmapped target property",
                "Can't map property",
                "No implementation type is registered for return type",
                "Could not generate implementation",
                "UnsupportedClassVersionError",
                "class file has wrong version",
                "no main manifest attribute");
    }

    private static boolean containsAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) return true;
        }
        return false;
    }
}
