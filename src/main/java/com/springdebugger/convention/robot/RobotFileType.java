package com.springdebugger.convention.robot;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Associates the {@code .robot} extension with plain text so the file is always parsed into an
 * inspectable PSI, even when no dedicated Robot Framework IDE plugin is installed. Without this, a
 * real IDE may treat {@code .robot} as an unknown (uninspectable) type and the convention checks
 * would silently never run.
 *
 * <p>Registered as a {@code secondary} association so that if the user does install a dedicated
 * Robot Framework plugin, that plugin's richer file type takes precedence (our checks then no longer
 * run on {@code .robot}; that is a documented limitation).
 */
public final class RobotFileType extends LanguageFileType {

    public static final RobotFileType INSTANCE = new RobotFileType();

    private RobotFileType() {
        super(PlainTextLanguage.INSTANCE, true);
    }

    @Override
    public @NotNull String getName() {
        return "Robot Framework (Spring Boot Debugger)";
    }

    @Override
    public @NotNull String getDescription() {
        return "Robot Framework suite files, checked by Spring Boot Debugger conventions";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "robot";
    }

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }
}
