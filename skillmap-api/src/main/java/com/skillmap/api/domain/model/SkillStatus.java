package com.skillmap.api.domain.model;

import com.skillmap.api.domain.exception.InvalidSkillStatusException;

import java.util.Arrays;
import java.util.Locale;

/**
 * Estado de una habilidad respecto al perfil del usuario.
 * Vive en el dominio porque es una regla del negocio, no un detalle técnico.
 */
public enum SkillStatus {
    MASTERED,
    IN_PROGRESS,
    PENDING;

    /** @throws InvalidSkillStatusException si falta o no es uno de los tres (sin distinguir mayúsculas) */
    public static SkillStatus fromValue(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(status -> status.name().equals(normalized))
                .findFirst()
                .orElseThrow(InvalidSkillStatusException::new);
    }
}
