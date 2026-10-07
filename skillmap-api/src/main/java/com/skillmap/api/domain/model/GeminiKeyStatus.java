package com.skillmap.api.domain.model;

/** Si hay key de Gemini y de dónde sale. Nunca lleva la key. */
public record GeminiKeyStatus(GeminiKeySource source) {

    public boolean configured() {
        return source != GeminiKeySource.NONE;
    }
}
