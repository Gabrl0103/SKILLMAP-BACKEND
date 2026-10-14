package com.skillmap.api.domain.model;

import java.util.Objects;

/**
 * Preferencias de la pantalla Ajustes. Hoy son de la instalación; cuando haya usuarios
 * pasarán a ser de cada uno sin cambiar este modelo.
 */
public record AppSettings(
        Theme theme,
        boolean showIntro,
        boolean reduceMotion
) {

    public static final AppSettings DEFAULTS = new AppSettings(Theme.LIGHT, true, false);

    public AppSettings {
        Objects.requireNonNull(theme, "theme");
    }

    /** Aplica solo los campos presentes en el cambio. */
    public AppSettings apply(AppSettingsUpdate update) {
        return new AppSettings(
                update.theme() != null ? update.theme() : theme,
                update.showIntro() != null ? update.showIntro() : showIntro,
                update.reduceMotion() != null ? update.reduceMotion() : reduceMotion);
    }
}
