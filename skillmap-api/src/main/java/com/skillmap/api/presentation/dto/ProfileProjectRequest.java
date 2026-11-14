package com.skillmap.api.presentation.dto;

import com.skillmap.api.presentation.validation.NoControlChars;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST y PUT /api/profile/projects: nombre (1 a 60 caracteres), descripción opcional (máx. 240),
 * URL opcional (http o https) y hasta 5 etiquetas de máx. 30 caracteres. PUT reemplaza todo.
 */
public record ProfileProjectRequest(
        @Size(max = 200, message = "El nombre del proyecto debe tener entre 1 y 60 caracteres.")
        @NoControlChars
        String name,
        @Size(max = 1000, message = "La descripción del proyecto puede tener como máximo 240 caracteres.")
        @NoControlChars(allowLineBreaks = true)
        String description,
        @Size(max = 2048, message = "La URL puede tener como máximo 2048 caracteres.")
        @NoControlChars(message = "La URL debe empezar por http:// o https:// (por ejemplo, https://mi-portfolio.com).")
        String url,
        @Size(max = 20, message = "Un proyecto puede tener como máximo 5 etiquetas.")
        List<@Size(max = 100, message = "Cada etiqueta puede tener como máximo 30 caracteres.") @NoControlChars String> tags) {
}
