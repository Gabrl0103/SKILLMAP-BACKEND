package com.skillmap.api.presentation.dto;

/**
 * PATCH /api/settings: solo se cambian los campos presentes (y no null). Los campos
 * desconocidos se ignoran.
 *
 * @param theme    "light" o "dark"
 * @param language "es" o "en"
 */
public record UpdateSettingsRequest(
        String theme,
        Boolean showIntro,
        Boolean reduceMotion,
        String language
) {
}
