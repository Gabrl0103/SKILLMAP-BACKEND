package com.skillmap.api.application.service;

import com.skillmap.api.domain.model.AnalysisHistory;
import com.skillmap.api.domain.model.AnalysisHistoryDetail;
import com.skillmap.api.domain.model.JobAnalysis;

import java.util.List;

public interface JobAnalyzerService {

    int DEFAULT_HISTORY_LIMIT = 20;
    /** Análisis que se guardan por usuario: al pasar de aquí se borra el más antiguo. */
    int MAX_HISTORY = 100;

    /**
     * Analiza la oferta y, si sale bien, la guarda en el historial del usuario. Si ese usuario ya analizó el
     * mismo texto, reutiliza sus habilidades sin llamar a la IA (engine "cache") y solo recalcula el match.
     */
    JobAnalysis analyze(Long userId, String jobDescription);

    /** Los más recientes primero; limit de 1 a MAX_HISTORY. */
    List<AnalysisHistory> getHistory(Long userId, int limit);

    /** Un análisis guardado con su resultado recalculado con el progreso actual, sin llamar a la IA. */
    AnalysisHistoryDetail getHistoryEntry(Long userId, Long id);

    void deleteHistoryEntry(Long userId, Long id);

    void clearHistory(Long userId);
}
