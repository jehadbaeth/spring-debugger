package com.springdebugger.tap;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.project.Project;
import com.springdebugger.SpringDebuggerService;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.ui.DiagnosisCardPanel;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Build tap for IntelliJ's internal (JPS) compiler. Registered via the
 * {@code com.intellij.compiler.task} extension point with {@code execute="AFTER"}
 * in plugin.xml, so it runs after every internal compilation.
 *
 * <p><b>Coverage limit:</b> this fires only when the project builds with IntelliJ's
 * own compiler. Most Spring Boot projects delegate builds to Gradle or Maven (the IDE
 * default), in which case the internal compiler never runs and this tap stays silent.
 * Delegated builds are handled by {@link ExternalBuildOutputTap}.
 */
public final class BuildOutputTap implements CompileTask {

    @Override
    public boolean execute(CompileContext context) {
        if (context.getMessageCount(CompilerMessageCategory.ERROR) == 0) return true;

        String errorText = Arrays.stream(context.getMessages(CompilerMessageCategory.ERROR))
                .map(m -> m.getMessage())
                .collect(Collectors.joining("\n"));

        BuildOutputAnalyzer analyzer = new BuildOutputAnalyzer(
                SpringDebuggerService.getInstance().getCatalog());
        Optional<DiagnosisCard> card = analyzer.analyze(errorText);

        Project project = context.getProject();
        card.ifPresent(c -> DiagnosisCardPanel.show(project, c));
        return true;
    }
}
