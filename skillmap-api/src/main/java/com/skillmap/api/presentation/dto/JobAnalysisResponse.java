package com.skillmap.api.presentation.dto;

import java.util.List;

/** Resultado del analizador: qué tanto encaja el usuario con la oferta y qué le falta. */
public record JobAnalysisResponse(
        int matchPercentage,
        List<String> masteredSkills,
        List<String> inProgressSkills,
        List<String> missingSkills
) {
}
