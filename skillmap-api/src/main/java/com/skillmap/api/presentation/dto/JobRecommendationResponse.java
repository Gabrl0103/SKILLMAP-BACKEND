package com.skillmap.api.presentation.dto;

import java.time.Instant;
import java.util.List;

/**
 * Oferta recomendada para el usuario. Sin la descripción completa: para analizarla se pide GET /api/jobs/{id}.
 *
 * @param masteredSkills las habilidades de la oferta que ya domina
 * @param missingSkills  las que le faltan, con su estado (IN_PROGRESS primero, luego PENDING)
 */
public record JobRecommendationResponse(
        Long id,
        String title,
        String company,
        String source,
        String url,
        Instant publishedAt,
        int matchPercentage,
        List<String> masteredSkills,
        List<MissingSkill> missingSkills
) {

    public record MissingSkill(String name, String status) {
    }
}
