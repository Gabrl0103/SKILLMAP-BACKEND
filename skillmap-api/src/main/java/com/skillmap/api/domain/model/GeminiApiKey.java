package com.skillmap.api.domain.model;

import com.skillmap.api.domain.exception.InvalidSettingsException;

/**
 * API key de Gemini con un formato aceptable. Solo se comprueba la forma, no que Gemini la
 * acepte. toString no la muestra: si acaba en un log o en una excepción, no se filtra.
 */
public final class GeminiApiKey {

    public static final int MIN_LENGTH = 20;
    public static final int MAX_LENGTH = 200;

    private final String value;

    private GeminiApiKey(String value) {
        this.value = value;
    }

    /**
     * Quita los espacios de los extremos (típicos al pegar) y valida el resto.
     *
     * @throws InvalidSettingsException con un mensaje que nunca incluye la key
     */
    public static GeminiApiKey of(String raw) {
        String key = raw == null ? "" : raw.strip();
        if (key.isEmpty()) {
            throw new InvalidSettingsException("La key de Gemini no puede estar vacía.");
        }
        if (key.chars().anyMatch(Character::isWhitespace)) {
            throw new InvalidSettingsException("La key de Gemini no puede contener espacios.");
        }
        if (key.length() < MIN_LENGTH || key.length() > MAX_LENGTH) {
            throw new InvalidSettingsException("La key de Gemini debe tener entre "
                    + MIN_LENGTH + " y " + MAX_LENGTH + " caracteres.");
        }
        if (key.chars().anyMatch(c -> c < 0x21 || c > 0x7E)) {
            throw new InvalidSettingsException(
                    "La key de Gemini solo puede contener letras, números y símbolos ASCII.");
        }
        return new GeminiApiKey(key);
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return "GeminiApiKey[***]";
    }
}
