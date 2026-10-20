package com.skillmap.api.domain.model;

/** Un análisis guardado y su resultado recalculado con el progreso actual del usuario (sin llamar a la IA). */
public record AnalysisHistoryDetail(AnalysisHistory entry, JobMatch current) {
}
