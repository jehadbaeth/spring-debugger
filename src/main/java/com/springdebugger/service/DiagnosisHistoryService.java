package com.springdebugger.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.springdebugger.model.Confidence;
import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.settings.SpringDebuggerSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Project-level service that stores the diagnosis history and notifies listeners
 * whenever a new card is added. All reads and writes are thread-safe.
 */
@Service(Service.Level.PROJECT)
public final class DiagnosisHistoryService {

    private final LinkedList<DiagnosisCard> history = new LinkedList<>();
    /** How many times each distinct diagnosis (by grouping key) has been seen this session. */
    private final Map<String, Integer> occurrences = new HashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    public static DiagnosisHistoryService getInstance(Project project) {
        return project.getService(DiagnosisHistoryService.class);
    }

    public synchronized void addDiagnosis(DiagnosisCard card) {
        SpringDebuggerSettings settings = SpringDebuggerSettings.getInstance();
        if (!settings.isEnabled()) return;

        Confidence minimum = settings.getMinimumConfidence();
        if (card.getConfidence().ordinal() > minimum.ordinal()) return;

        // Group repeats: the same diagnosis (rule + text) collapses to one row with a count
        // that bumps each time it recurs, so a broken endpoint hit many times during an
        // integration run does not flood the history.
        String key = card.groupingKey();
        if (occurrences.containsKey(key)) {
            occurrences.put(key, occurrences.get(key) + 1);
            history.removeIf(c -> c.groupingKey().equals(key));
        } else {
            occurrences.put(key, 1);
        }

        history.addFirst(card);
        int max = settings.getMaxHistorySize();
        while (history.size() > max) {
            DiagnosisCard removed = history.removeLast();
            occurrences.remove(removed.groupingKey());
        }

        notifyListeners();
    }

    /** How many times the given diagnosis has occurred this session (>= 1). */
    public synchronized int getOccurrences(DiagnosisCard card) {
        return occurrences.getOrDefault(card.groupingKey(), 1);
    }

    public synchronized List<DiagnosisCard> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public synchronized void clear() {
        history.clear();
        occurrences.clear();
        notifyListeners();
    }

    public synchronized DiagnosisCard getMostRecent() {
        return history.isEmpty() ? null : history.getFirst();
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        ApplicationManager.getApplication().invokeLater(
            () -> listeners.forEach(Runnable::run)
        );
    }
}
