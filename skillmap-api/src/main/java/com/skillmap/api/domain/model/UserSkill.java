package com.skillmap.api.domain.model;

import java.time.Instant;

/**
 * Progreso de un usuario en una habilidad del catálogo. Uno por (userId, skillId);
 * sin fila, la habilidad está pendiente para ese usuario.
 *
 * @param updatedAt cuándo pasó a este estado. Repetir el mismo estado no la cambia (ver SkillServiceImpl). Las filas
 *                  anteriores a esta columna la reciben al arrancar: la fecha de alta de la cuenta (UserSkillDatesBackfill).
 */
public record UserSkill(Long userId, Long skillId, SkillStatus status, Instant updatedAt) {
}
