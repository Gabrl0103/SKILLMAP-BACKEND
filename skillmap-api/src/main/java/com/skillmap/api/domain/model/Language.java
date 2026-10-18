package com.skillmap.api.domain.model;

import com.skillmap.api.domain.exception.InvalidSettingsException;

import java.util.Arrays;
import java.util.Locale;

/** Idioma de la interfaz. {@link #value()} es como viaja en la API y como se guarda. */
public enum Language {
    ES("es"),
    EN("en");

    private final String value;

    Language(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** @throws InvalidSettingsException si no es "es" ni "en" (sin distinguir mayúsculas) */
    public static Language fromValue(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(language -> language.value.equals(normalized))
                .findFirst()
                // Sin repetir lo recibido: el mensaje llega al cliente.
                .orElseThrow(() -> new InvalidSettingsException(
                        "El idioma no es válido. Valores permitidos: es, en."));
    }
}
