package com.skillmap.api.presentation.dto;

import java.time.Instant;

/** Un análisis del historial, sin el texto de la oferta. matchPercentage: el de ese momento. */
public record AnalysisHistoryItemResponse(
        Long id,
        String title,
        String engine,
        int matchPercentage,
        int skillCount,
        Instant createdAt
) {
}
