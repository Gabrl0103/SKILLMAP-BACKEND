package com.skillmap.api.domain.model;

import java.util.Locale;

/** De dónde sale la API key de Gemini que se usa ahora mismo. */
public enum GeminiKeySource {
    /** Variable de entorno GEMINI_API_KEY: tiene prioridad sobre el archivo. */
    ENV,
    /** Archivo de ajustes del usuario (lo que se guarda desde la pantalla Ajustes). */
    FILE,
    NONE;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
