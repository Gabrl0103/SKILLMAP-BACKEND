package com.skillmap.api.domain.model;

import com.skillmap.api.domain.exception.InvalidSettingsException;

import java.util.Arrays;
import java.util.Locale;

/** Tema visual de la app. {@link #value()} es como viaja en la API y como se guarda. */
public enum Theme {
    LIGHT("light"),
    DARK("dark");

    private final String value;

    Theme(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** @throws InvalidSettingsException si no es "light" ni "dark" (sin distinguir mayúsculas) */
    public static Theme fromValue(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(theme -> theme.value.equals(normalized))
                .findFirst()
                // Sin repetir lo recibido: el mensaje llega al cliente.
                .orElseThrow(() -> new InvalidSettingsException(
                        "El tema no es válido. Valores permitidos: light, dark."));
    }
}
