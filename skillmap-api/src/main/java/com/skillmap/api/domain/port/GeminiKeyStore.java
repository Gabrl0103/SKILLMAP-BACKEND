package com.skillmap.api.domain.port;

import com.skillmap.api.domain.model.GeminiApiKey;
import com.skillmap.api.domain.model.GeminiKeySource;

import java.util.Optional;

/**
 * Puerto de salida: dónde vive la API key de Gemini. Se consulta en cada llamada, así un
 * cambio se nota sin reiniciar. Ninguna implementación debe registrar la key.
 */
public interface GeminiKeyStore {

    /** La key que se usa ahora mismo, respetando la precedencia de las fuentes. */
    Optional<String> findKey();

    /** De qué fuente sale la key de {@link #findKey()}. */
    GeminiKeySource activeSource();

    /**
     * Guarda la key en el almacén editable por el usuario. Si otra fuente tiene prioridad,
     * se guarda igual pero {@link #activeSource()} sigue indicando esa otra.
     *
     * @throws com.skillmap.api.domain.exception.SettingsStorageException si no se pudo guardar
     */
    void save(GeminiApiKey key);
}
