package com.skillmap.api.presentation.dto;

import com.skillmap.api.presentation.validation.NoControlChars;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST y PUT /api/goals/custom: nombre (2–40), descripción opcional (≤ 120) y de 3 a 8 ids de habilidades. Aquí solo
 * los topes gruesos (Bean Validation); las reglas exactas están en CustomGoalFactory.
 */
public record CustomGoalRequest(
        @Size(max = 200, message = "El nombre del objetivo debe tener entre 2 y 40 caracteres.")
        @NoControlChars(message = "Usa solo texto: sin los caracteres < ni >.")
        String name,
        @Size(max = 1000, message = "La descripción puede tener como máximo 120 caracteres.")
        @NoControlChars(message = "Usa solo texto: sin los caracteres < ni >.")
        String description,
        @Size(max = 50, message = "Elige entre 3 y 8 habilidades.")
        List<Long> skillIds) {
}
