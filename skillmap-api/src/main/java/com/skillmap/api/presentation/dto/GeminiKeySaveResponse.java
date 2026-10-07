package com.skillmap.api.presentation.dto;

/**
 * Respuesta de PUT /api/settings/gemini-key cuando la key se guardó pero no es la que se usa
 * (la variable de entorno GEMINI_API_KEY tiene prioridad). Sin la key.
 */
public record GeminiKeySaveResponse(
        boolean configured,
        String source,
        String warning
) {
}
