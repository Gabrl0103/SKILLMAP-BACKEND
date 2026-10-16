package com.skillmap.api.infrastructure.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Lo que el adaptador de IA necesita saber de la API key de Gemini. De dónde sale y con qué
 * precedencia (variable de entorno GEMINI_API_KEY, luego config.properties) lo decide
 * {@link PropertiesFileGeminiKeyStore}. Se consulta en cada llamada: una key guardada desde
 * Ajustes se usa sin reiniciar. La key nunca se escribe en los logs.
 */
@Component
public class GeminiApiKeyProvider {

    private final Supplier<Optional<String>> key;
    private final Path configFile;

    @Autowired
    public GeminiApiKeyProvider(PropertiesFileGeminiKeyStore keyStore) {
        this(keyStore::findKey, keyStore.getConfigFile());
    }

    private GeminiApiKeyProvider(Supplier<Optional<String>> key, Path configFile) {
        this.key = key;
        this.configFile = configFile;
    }

    /** Perfil cloud: solo la variable de entorno GEMINI_API_KEY, sin archivo de ajustes. */
    public static GeminiApiKeyProvider fromEnvironmentVariable() {
        return new GeminiApiKeyProvider(
                () -> Optional.ofNullable(System.getenv(PropertiesFileGeminiKeyStore.ENV_VARIABLE))
                        .map(String::strip)
                        .filter(k -> !k.isEmpty()),
                null);
    }

    public Optional<String> getApiKey() {
        return key.get();
    }

    public Optional<Path> getConfigFile() {
        return Optional.ofNullable(configFile);
    }
}
