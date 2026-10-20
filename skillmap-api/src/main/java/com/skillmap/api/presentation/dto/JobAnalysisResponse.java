package com.skillmap.api.presentation.dto;

import java.util.List;

/**
 * Resultado del analizador: qué tanto encaja el usuario con la oferta y qué le falta. engine: "gemini",
 * "remote" o "cache" (habilidades reutilizadas de un análisis anterior de la misma oferta, sin llamar a la IA).
 * historyId: el registro del historial (null si no se pudo guardar).
 */
public record JobAnalysisResponse(
        int matchPercentage,
        List<String> masteredSkills,
        List<String> inProgressSkills,
        List<String> missingSkills,
        String engine,
        Long historyId
) {
}
