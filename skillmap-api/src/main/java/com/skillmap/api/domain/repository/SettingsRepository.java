package com.skillmap.api.domain.repository;

import com.skillmap.api.domain.model.AppSettings;

/** Puerto de salida: dónde se guardan los ajustes de la app. */
public interface SettingsRepository {
    /** Los ajustes guardados; los que falten (o no se entiendan) toman su valor por defecto. */
    AppSettings load();
    void save(AppSettings settings);
}
