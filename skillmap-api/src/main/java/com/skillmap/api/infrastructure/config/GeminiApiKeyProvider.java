package com.skillmap.api.infrastructure.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Único punto que sabe de dónde sale la API key de Gemini. Hoy se lee de la
 * variable de entorno GEMINI_API_KEY; cuando exista la pantalla de ajustes,
 * solo cambia esta clase y el adaptador de IA no se entera.
 */
@Component
public class GeminiApiKeyProvider {

    static final String ENV_VARIABLE = "GEMINI_API_KEY";

    private final Environment environment;

    public GeminiApiKeyProvider(Environment environment) {
        this.environment = environment;
    }

    public Optional<String> getApiKey() {
        return Optional.ofNullable(environment.getProperty(ENV_VARIABLE))
                .map(String::strip)
                .filter(key -> !key.isEmpty());
    }
}
