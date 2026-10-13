package com.skillmap.api.domain.model;

/**
 * Progreso de un usuario en una habilidad del catálogo. Uno por (userId, skillId);
 * sin fila, la habilidad está pendiente para ese usuario.
 */
public record UserSkill(Long userId, Long skillId, SkillStatus status) {
}
