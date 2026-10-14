package com.skillmap.api.domain.model;

/** Cambio parcial de los ajustes: null = no se toca ese campo. */
public record AppSettingsUpdate(
        Theme theme,
        Boolean showIntro,
        Boolean reduceMotion
) {
}
