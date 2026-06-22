package com.springdebugger.settings;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class SpringDebuggerSettingsConfigurable implements Configurable {

    private SpringDebuggerSettingsPanel panel;

    @Nls
    @Override
    public String getDisplayName() {
        return "Spring Boot Debugger";
    }

    @Override
    public @Nullable JComponent createComponent() {
        panel = new SpringDebuggerSettingsPanel();
        return panel.getComponent();
    }

    @Override
    public boolean isModified() {
        return panel != null && panel.isModified();
    }

    @Override
    public void apply() {
        if (panel != null) panel.apply();
    }

    @Override
    public void reset() {
        if (panel != null) panel.reset();
    }

    @Override
    public void disposeUIResources() {
        panel = null;
    }
}
