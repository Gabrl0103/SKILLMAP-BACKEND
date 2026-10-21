package com.skillmap.api.domain.model;

import java.util.List;

/**
 * Oferta guardada que encaja con el objetivo del usuario, con su encaje calculado con el progreso actual.
 *
 * @param matchPercentage % de las habilidades detectadas en la oferta que el usuario ya tiene (aprendiendo cuenta la mitad)
 * @param goalSkillCount  cuántas de las habilidades detectadas son del objetivo
 * @param masteredSkills  las que ya domina, en el orden del catálogo
 * @param missingSkills   las que le faltan: primero las que está aprendiendo y luego las pendientes
 */
public record JobRecommendation(
        JobPosting posting,
        int matchPercentage,
        int goalSkillCount,
        List<String> masteredSkills,
        List<MissingSkill> missingSkills
) {

    /** Habilidad de la oferta que el usuario aún no domina: IN_PROGRESS o PENDING. */
    public record MissingSkill(String name, SkillStatus status) {
    }
}
