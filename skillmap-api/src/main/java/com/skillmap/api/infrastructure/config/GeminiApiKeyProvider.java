package com.skillmap.api.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * Único punto que sabe de dónde sale la API key de Gemini: la variable de entorno
 * GEMINI_API_KEY o, si no existe, gemini.api.key en el archivo de ajustes del usuario
 * (skillmap.config-file, por defecto %APPDATA%\SkillMap\config.properties). Cuando exista
 * la pantalla de ajustes, solo cambia esta clase y el adaptador de IA no se entera.
 * La key nunca se escribe en los logs.
 */
@Component
public class GeminiApiKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiApiKeyProvider.class);
    static final String ENV_VARIABLE = "GEMINI_API_KEY";
    static final String CONFIG_FILE_PROPERTY = "skillmap.config-file";
    static final String CONFIG_FILE_KEY = "gemini.api.key";

    private final Environment environment;

    public GeminiApiKeyProvider(Environment environment) {
        this.environment = environment;
    }

    public Optional<String> getApiKey() {
        return clean(environment.getProperty(ENV_VARIABLE)).or(this::readFromConfigFile);
    }

    public Optional<Path> getConfigFile() {
        return Optional.ofNullable(environment.getProperty(CONFIG_FILE_PROPERTY)).map(Path::of);
    }

    /** Se lee en cada llamada: si el usuario edita el archivo no hace falta reiniciar. */
    private Optional<String> readFromConfigFile() {
        Optional<Path> file = getConfigFile().filter(Files::isRegularFile);
        if (file.isEmpty()) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try {
            // El Bloc de notas puede guardar con BOM: sin quitarlo la primera clave no se encontraría.
            String content = Files.readString(file.get(), StandardCharsets.UTF_8);
            properties.load(new StringReader(content.startsWith("﻿") ? content.substring(1) : content));
        } catch (IOException | IllegalArgumentException e) {
            // Solo la ruta y el tipo de error: el mensaje podría incluir parte del contenido.
            log.warn("No se pudo leer {} ({})", file.get(), e.getClass().getSimpleName());
            return Optional.empty();
        }
        return clean(properties.getProperty(CONFIG_FILE_KEY));
    }

    private static Optional<String> clean(String key) {
        return Optional.ofNullable(key)
                .map(String::strip)
                .filter(k -> !k.isEmpty());
    }
}
