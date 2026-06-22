package com.springdebugger.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.springdebugger.model.Confidence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
    name = "SpringDebuggerSettings",
    storages = @Storage("spring-debugger.xml")
)
@Service(Service.Level.APP)
public final class SpringDebuggerSettings implements PersistentStateComponent<SpringDebuggerSettings.State> {

    public static class State {
        public boolean enabled = true;
        public String minimumConfidence = "MEDIUM";
        public int maxHistorySize = 30;
        public boolean llmEnabled = false;
        public String ollamaBaseUrl = "http://localhost:11434";
        public String ollamaModel = "llama3.2";
        public boolean showNotificationBalloon = true;
        public boolean focusToolWindowOnError = true;
    }

    private State state = new State();

    public static SpringDebuggerSettings getInstance() {
        return ApplicationManager.getApplication().getService(SpringDebuggerSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public boolean isEnabled() { return state.enabled; }
    public void setEnabled(boolean enabled) { state.enabled = enabled; }

    public Confidence getMinimumConfidence() {
        try { return Confidence.valueOf(state.minimumConfidence); }
        catch (IllegalArgumentException e) { return Confidence.MEDIUM; }
    }
    public void setMinimumConfidence(Confidence c) { state.minimumConfidence = c.name(); }

    public int getMaxHistorySize() { return state.maxHistorySize; }
    public void setMaxHistorySize(int size) { state.maxHistorySize = size; }

    public boolean isLlmEnabled() { return state.llmEnabled; }
    public void setLlmEnabled(boolean enabled) { state.llmEnabled = enabled; }

    public String getOllamaBaseUrl() { return state.ollamaBaseUrl; }
    public void setOllamaBaseUrl(String url) { state.ollamaBaseUrl = url; }

    public String getOllamaModel() { return state.ollamaModel; }
    public void setOllamaModel(String model) { state.ollamaModel = model; }

    public boolean isShowNotificationBalloon() { return state.showNotificationBalloon; }
    public void setShowNotificationBalloon(boolean show) { state.showNotificationBalloon = show; }

    public boolean isFocusToolWindowOnError() { return state.focusToolWindowOnError; }
    public void setFocusToolWindowOnError(boolean focus) { state.focusToolWindowOnError = focus; }
}
