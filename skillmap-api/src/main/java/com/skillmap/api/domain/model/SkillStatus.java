package com.skillmap.api.domain.model;

/**
 * Estado de una habilidad respecto al perfil del usuario.
 * Vive en el dominio porque es una regla del negocio, no un detalle técnico.
 */
public enum SkillStatus {
    MASTERED,
    IN_PROGRESS,
    PENDING
}
