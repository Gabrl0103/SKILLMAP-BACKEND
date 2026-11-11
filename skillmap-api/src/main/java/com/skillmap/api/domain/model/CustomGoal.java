package com.skillmap.api.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Objetivo propio de un usuario ("Otro objetivo"): nombre, descripción opcional y de 3 a 8 habilidades del catálogo.
 * Se crea y se valida con CustomGoalFactory. Para todo lo demás (ruta, % de preparación, ofertas…) se usa como un
 * CareerGoal más (toCareerGoal), con la misma importancia para todas sus habilidades.
 */
public record CustomGoal(Long id, Long userId, String name, String description, List<Long> skillIds,
                         Instant createdAt, Instant updatedAt) {

    /** Importancia de cada habilidad de un objetivo propio: todas pesan igual (el % es dominadas / total). */
    public static final int SKILL_IMPORTANCE = 3;

    public CustomGoal {
        skillIds = List.copyOf(skillIds);
    }

    public CareerGoal toCareerGoal() {
        List<GoalSkill> skills = skillIds.stream().map(id -> new GoalSkill(id, SKILL_IMPORTANCE)).toList();
        return new CareerGoal(id, name, description, skills, userId);
    }

    /** Copia editada: mismo id, dueño y fecha de creación. */
    public CustomGoal withChanges(String newName, String newDescription, List<Long> newSkillIds, Instant editedAt) {
        return new CustomGoal(id, userId, newName, newDescription, newSkillIds, createdAt, editedAt);
    }
}
