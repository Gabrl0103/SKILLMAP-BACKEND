package com.skillmap.api.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Copia de los datos de una cuenta para "Exportar mis datos" (Ajustes): perfil, objetivo, estado de cada habilidad,
 * enlaces, proyectos, análisis guardados e historial de preparación. Nunca lleva el hash de la contraseña, tokens de
 * sesión ni la key de Gemini: el usuario se copia campo a campo, sin el hash.
 */
public record AccountExport(
        String name,
        String email,
        String headline,
        Instant createdAt,
        CareerGoal goal,
        List<Skill> skills,
        List<ProfileLink> links,
        List<ProfileProject> projects,
        List<AnalysisHistory> analyses,
        List<ReadinessSnapshot> readiness,
        List<CareerGoal> goals,
        Instant exportedAt
) {
}
