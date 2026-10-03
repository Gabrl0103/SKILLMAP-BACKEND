package com.skillmap.api.domain.factory;

import com.skillmap.api.domain.model.DemandSource;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SkillStatus;

/**
 * Patrón Factory: centraliza y valida la creación de una Skill.
 * Así ningún otro punto del código puede construir una habilidad inválida
 * (nombre vacío o demanda fuera de rango).
 */
public final class SkillFactory {

    private SkillFactory() {
        // Clase de utilidad: no se instancia.
    }

    /** Para dar de alta una habilidad nueva, todavía sin id (lo asigna la base de datos). */
    public static Skill createNew(String name, String category, int demandPercentage) {
        validate(name, demandPercentage);
        return new Skill(null, name, category, demandPercentage, SkillStatus.PENDING, DemandSource.SEEDED);
    }

    /** Para reconstruir una habilidad que ya existe (viene de la base de datos). */
    public static Skill reconstruct(Long id, String name, String category, int demandPercentage, SkillStatus status,
                                    DemandSource demandSource) {
        validate(name, demandPercentage);
        return new Skill(id, name, category, demandPercentage, status, demandSource);
    }

    private static void validate(String name, int demandPercentage) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la habilidad no puede estar vacío");
        }
        if (demandPercentage < 0 || demandPercentage > 100) {
            throw new IllegalArgumentException("El porcentaje de demanda debe estar entre 0 y 100");
        }
    }
}
