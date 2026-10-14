package com.skillmap.api.application.service;

import com.skillmap.api.domain.model.AppSettings;
import com.skillmap.api.domain.model.AppSettingsUpdate;
import com.skillmap.api.domain.model.GeminiKeyStatus;

/** Puerto de entrada: casos de uso de la pantalla Ajustes. */
public interface SettingsService {
    AppSettings getSettings();
    /** Aplica solo los campos presentes y devuelve los ajustes resultantes. */
    AppSettings updateSettings(AppSettingsUpdate update);
    GeminiKeyStatus getGeminiKeyStatus();
    /**
     * Valida el formato de la key (sin llamar a Gemini) y la guarda.
     *
     * @return el estado tras guardarla: si hay variable de entorno, su fuente sigue siendo esa
     * @throws com.skillmap.api.domain.exception.InvalidSettingsException si el formato no es válido
     */
    GeminiKeyStatus saveGeminiKey(String rawKey);
}
