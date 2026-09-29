package com.skillmap.api.domain.model;

/**
 * Relación entre un objetivo laboral y una habilidad: "para ser X necesitas Y,
 * y Y pesa tanto (importance de 1 a 5)". Es un value object: inmutable.
 */
public record GoalSkill(Long skillId, int importance) {
}
