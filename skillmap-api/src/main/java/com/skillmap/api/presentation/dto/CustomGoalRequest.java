package com.skillmap.api.presentation.dto;

import com.skillmap.api.presentation.validation.NoControlChars;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST y PUT /api/goals/custom: nombre (2–40), descripción opcional (≤ 120) y de 3 a 8 habilidades en total: ids
 * (del catálogo o propias de este objetivo) y, opcional, newSkills: nombres de habilidades nuevas que escribe el
 * usuario (2–40 caracteres cada una; se crean como propias de este objetivo). Sin newSkills funciona como antes. Aquí
 * solo los topes gruesos (Bean Validation); las reglas exactas están en CustomGoalFactory.
 */
public record CustomGoalRequest(
        @Size(max = 200, message = "El nombre del objetivo debe tener entre 2 y 40 caracteres.")
        @NoControlChars(message = "Usa solo texto: sin los caracteres < ni >.")
        String name,
        @Size(max = 1000, message = "La descripción puede tener como máximo 120 caracteres.")
        @NoControlChars(message = "Usa solo texto: sin los caracteres < ni >.")
        String description,
        @Size(max = 50, message = "Elige entre 3 y 8 habilidades.")
        List<Long> skillIds,
        @Size(max = 50, message = "Elige entre 3 y 8 habilidades.")
        List<@Size(max = 200, message = "Cada habilidad propia debe tener entre 2 y 40 caracteres.")
             @NoControlChars(message = "Usa solo texto: sin los caracteres < ni >.") String> newSkills) {

    public CustomGoalRequest(String name, String description, List<Long> skillIds) {
        this(name, description, skillIds, null);
    }

    /** Nunca null. */
    public List<String> newSkillsOrEmpty() {
        return newSkills == null ? List.of() : newSkills;
    }
}
