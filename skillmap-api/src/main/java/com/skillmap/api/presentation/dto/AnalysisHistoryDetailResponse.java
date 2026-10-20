package com.skillmap.api.presentation.dto;

import java.time.Instant;
import java.util.List;

/**
 * Un análisis guardado. matchPercentage y las tres listas están recalculados con el progreso actual
 * (recalculated siempre true); savedMatchPercentage es el que salió cuando se analizó.
 */
public record AnalysisHistoryDetailResponse(
        Long id,
        String title,
        String jobDescription,
        String engine,
        Instant createdAt,
        int savedMatchPercentage,
        boolean recalculated,
        int matchPercentage,
        List<String> masteredSkills,
        List<String> inProgressSkills,
        List<String> missingSkills
) {
}
