package com.skillmap.api.domain.model;

import java.util.List;

/** Resultado de comparar una oferta laboral con las habilidades del usuario. */
public record JobMatch(
        int matchPercentage,
        List<String> mastered,
        List<String> inProgress,
        List<String> missing
) {
}
