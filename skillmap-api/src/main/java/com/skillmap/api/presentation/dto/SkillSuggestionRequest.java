package com.skillmap.api.presentation.dto;

import com.skillmap.api.presentation.validation.NoControlChars;
import jakarta.validation.constraints.Size;

/** POST /api/goals/custom/suggestions: el nombre del rol tal como lo escribe el usuario. */
public record SkillSuggestionRequest(
        @Size(max = 200, message = "Escribe el nombre del rol (de 2 a 40 caracteres) para sugerir habilidades.")
        @NoControlChars(message = "Escribe el nombre del rol (de 2 a 40 caracteres) para sugerir habilidades.")
        String role) {
}
