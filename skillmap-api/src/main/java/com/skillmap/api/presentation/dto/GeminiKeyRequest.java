package com.skillmap.api.presentation.dto;

/**
 * PUT /api/settings/gemini-key. toString no muestra la key: Spring registra el cuerpo leído
 * en DEBUG y así no puede acabar en un log.
 */
public record GeminiKeyRequest(String key) {

    @Override
    public String toString() {
        return "GeminiKeyRequest[key=***]";
    }
}
