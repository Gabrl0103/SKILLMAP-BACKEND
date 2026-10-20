package com.skillmap.api.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Un análisis exitoso del Analizador, guardado para el usuario. result es el resultado tal como se vio
 * en ese momento (con el progreso de entonces); sus habilidades sirven de caché para la misma oferta.
 * toString no incluye el texto de la oferta: nunca debe acabar en un log.
 */
public record AnalysisHistory(Long id, Long userId, String title, String jobDescription, String textHash,
                              String engine, JobMatch result, Instant createdAt) {

    /** Todas las habilidades que pidió la oferta, sin importar su estado. */
    public List<String> detectedSkills() {
        List<String> skills = new ArrayList<>(result.mastered());
        skills.addAll(result.inProgress());
        skills.addAll(result.missing());
        return skills;
    }

    @Override
    public String toString() {
        return "AnalysisHistory[id=" + id + ", userId=" + userId + ", engine=" + engine
                + ", matchPercentage=" + result.matchPercentage() + ", createdAt=" + createdAt + "]";
    }
}
