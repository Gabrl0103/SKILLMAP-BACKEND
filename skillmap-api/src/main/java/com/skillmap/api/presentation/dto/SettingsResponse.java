package com.skillmap.api.presentation.dto;

/**
 * GET y PATCH /api/settings: preferencias de la pantalla Ajustes y estado de la key de Gemini.
 * La key nunca viaja en la respuesta, ni entera ni en parte.
 */
public record SettingsResponse(
        String theme,
        boolean showIntro,
        boolean reduceMotion,
        GeminiStatus gemini
) {

    /** source: "env" (variable de entorno, tiene prioridad), "file" (config.properties) o "none". */
    public record GeminiStatus(boolean configured, String source) {
    }
}
