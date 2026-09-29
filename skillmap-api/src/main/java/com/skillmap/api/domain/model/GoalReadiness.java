package com.skillmap.api.domain.model;

import java.util.List;

/** Resultado de evaluar un objetivo: porcentaje, conteos y brechas (habilidades aún no dominadas). */
public record GoalReadiness(
        CareerGoal goal,
        int readinessPercentage,
        int masteredCount,
        int totalCount,
        List<Skill> gaps
) {
}
