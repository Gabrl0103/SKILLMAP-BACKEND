package com.skillmap.api.infrastructure.config;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Lo que el adaptador de IA necesita saber de la API key de Gemini. De dónde sale y con qué
 * precedencia (variable de entorno GEMINI_API_KEY, luego config.properties) lo decide
 * {@link PropertiesFileGeminiKeyStore}. Se consulta en cada llamada: una key guardada desde
 * Ajustes se usa sin reiniciar. La key nunca se escribe en los logs.
 */
@Component
public class GeminiApiKeyProvider {

    private final PropertiesFileGeminiKeyStore keyStore;

    public GeminiApiKeyProvider(PropertiesFileGeminiKeyStore keyStore) {
        this.keyStore = keyStore;
    }

    public Optional<String> getApiKey() {
        return keyStore.findKey();
    }

    public Optional<Path> getConfigFile() {
        return Optional.of(keyStore.getConfigFile());
    }
}
