package com.skillmap.api.presentation.dto;

import java.util.List;

/**
 * POST y PUT /api/profile/projects: nombre (1 a 60 caracteres), descripción opcional (máx. 240),
 * URL opcional (http o https) y hasta 5 etiquetas de máx. 30 caracteres. PUT reemplaza todo.
 */
public record ProfileProjectRequest(String name, String description, String url, List<String> tags) {
}
