package com.springdebugger.tap;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.project.Project;
import com.springdebugger.SpringDebuggerService;
import com.springdebugger.engine.DiagnosisPipeline;
import com.springdebugger.extractor.LogExtractor;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.model.Phase;
import com.springdebugger.model.RawSignal;
import com.springdebugger.ui.DiagnosisCardPanel;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Runs after a compilation step and analyses Spring-specific build errors.
 * Registered programmatically via CompilerManager in SpringDebuggerStartupActivity.
 */
public final class BuildOutputTap implements CompileTask {

    private final LogExtractor extractor = new LogExtractor();

    @Override
    public boolean execute(CompileContext context) {
        if (context.getMessageCount(CompilerMessageCategory.ERROR) == 0) return true;

        String errorText = Arrays.stream(context.getMessages(CompilerMessageCategory.ERROR))
                .map(m -> m.getMessage())
                .collect(Collectors.joining("\n"));

        if (!isSpringRelated(errorText)) return true;

        DiagnosisPipeline pipeline = new DiagnosisPipeline(
                SpringDebuggerService.getInstance().getCatalog());

        RawSignal signal = extractor.extract(errorText, Phase.COMPILE);
        Optional<DiagnosisCard> card = pipeline.run(signal);
        Project project = context.getProject();
        card.ifPresent(c -> DiagnosisCardPanel.show(project, c));

        return true;
    }

    private boolean isSpringRelated(String text) {
        return text.contains("WebSecurityConfigurerAdapter")
                || text.contains("spring-boot-configuration-processor")
                || text.contains("Lombok")
                || text.contains("MapStruct")
                || text.contains("UnsupportedClassVersionError");
    }
}
