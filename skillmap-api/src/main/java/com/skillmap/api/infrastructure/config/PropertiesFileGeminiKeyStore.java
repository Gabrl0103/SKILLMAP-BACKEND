package com.skillmap.api.infrastructure.config;

import com.skillmap.api.domain.exception.SettingsStorageException;
import com.skillmap.api.domain.model.GeminiApiKey;
import com.skillmap.api.domain.model.GeminiKeySource;
import com.skillmap.api.domain.port.GeminiKeyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Adaptador del puerto GeminiKeyStore. La key sale de la variable de entorno GEMINI_API_KEY
 * o, si no existe, de gemini.api.key en config.properties dentro de skillmap.config-dir
 * (por defecto %APPDATA%\SkillMap). Se lee en cada llamada: un cambio en el archivo, a mano
 * o desde Ajustes, se nota sin reiniciar. Al guardar solo se toca la línea de la key; el resto
 * del archivo (otras propiedades, comentarios, orden) queda igual. La key nunca va a los logs.
 */
@Component
public class PropertiesFileGeminiKeyStore implements GeminiKeyStore {

    private static final Logger log = LoggerFactory.getLogger(PropertiesFileGeminiKeyStore.class);
    static final String ENV_VARIABLE = "GEMINI_API_KEY";
    static final String CONFIG_FILE_NAME = "config.properties";
    static final String CONFIG_FILE_KEY = "gemini.api.key";
    private static final String UTF8_BOM = "﻿";
    /** El BOM de UTF-8 leído como ISO-8859-1, que es como se lee el archivo al editarlo. */
    private static final String UTF8_BOM_AS_LATIN1 = "ï»¿";

    private final Environment environment;
    private final Path configFile;

    @Autowired
    public PropertiesFileGeminiKeyStore(Environment environment,
                                        @Value("${skillmap.config-dir}") String configDir) {
        this(environment, Path.of(configDir));
    }

    public PropertiesFileGeminiKeyStore(Environment environment, Path configDir) {
        this.environment = environment;
        this.configFile = configDir.resolve(CONFIG_FILE_NAME);
    }

    public Path getConfigFile() {
        return configFile;
    }

    @Override
    public Optional<String> findKey() {
        return fromEnvironment().or(this::readFromConfigFile);
    }

    @Override
    public GeminiKeySource activeSource() {
        if (fromEnvironment().isPresent()) {
            return GeminiKeySource.ENV;
        }
        return readFromConfigFile().isPresent() ? GeminiKeySource.FILE : GeminiKeySource.NONE;
    }

    /** synchronized: dos guardados a la vez no pueden mezclar sus líneas. */
    @Override
    public synchronized void save(GeminiApiKey key) {
        try {
            Files.createDirectories(configFile.getParent());
            // ISO-8859-1 conserva cualquier byte tal cual: lo que no es la key se reescribe idéntico
            // aunque el archivo no sea UTF-8. La key es ASCII, igual en ambas codificaciones.
            String original = Files.isRegularFile(configFile)
                    ? Files.readString(configFile, StandardCharsets.ISO_8859_1)
                    : "";
            writeAtomically(withKey(original, key.value()));
        } catch (IOException e) {
            // Solo la ruta y el tipo de error; sin causa, para que nada pueda arrastrar contenido.
            log.warn("No se pudo guardar la key de Gemini en {} ({})", configFile, e.getClass().getSimpleName());
            throw new SettingsStorageException("No se pudo guardar la key de Gemini en el archivo de ajustes.", null);
        }
    }

    private Optional<String> fromEnvironment() {
        return clean(environment.getProperty(ENV_VARIABLE));
    }

    private Optional<String> readFromConfigFile() {
        if (!Files.isRegularFile(configFile)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try {
            // El Bloc de notas puede guardar con BOM: sin quitarlo la primera clave no se encontraría.
            String content = Files.readString(configFile, StandardCharsets.UTF_8);
            properties.load(new StringReader(content.startsWith(UTF8_BOM) ? content.substring(1) : content));
        } catch (IOException | IllegalArgumentException e) {
            // Solo la ruta y el tipo de error: el mensaje podría incluir parte del contenido.
            log.warn("No se pudo leer {} ({})", configFile, e.getClass().getSimpleName());
            return Optional.empty();
        }
        return clean(properties.getProperty(CONFIG_FILE_KEY));
    }

    /**
     * Sustituye la primera aparición de gemini.api.key (con sus líneas de continuación) y quita
     * las demás, que si no ganarían al leer; si no estaba, la añade al final.
     */
    static String withKey(String content, String key) {
        String newline = content.contains("\r\n") ? "\r\n" : content.contains("\n") ? "\n" : System.lineSeparator();
        String bom = content.startsWith(UTF8_BOM_AS_LATIN1) ? UTF8_BOM_AS_LATIN1 : "";
        List<String> lines = new ArrayList<>(List.of(content.substring(bom.length()).split("\r\n|\n|\r", -1)));
        if (lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        String keyLine = CONFIG_FILE_KEY + "=" + key.replace("\\", "\\\\");

        List<String> result = new ArrayList<>();
        boolean written = false;
        boolean continuation = false;
        boolean dropping = false;
        for (String line : lines) {
            if (continuation) {
                continuation = endsWithContinuation(line);
                if (!dropping) {
                    result.add(line);
                }
                continue;
            }
            boolean isKey = CONFIG_FILE_KEY.equals(propertyKey(line));
            continuation = !isComment(line) && endsWithContinuation(line);
            dropping = isKey;
            if (!isKey) {
                result.add(line);
            } else if (!written) {
                result.add(keyLine);
                written = true;
            }
        }
        if (!written) {
            result.add(keyLine);
        }
        return bom + String.join(newline, result) + newline;
    }

    /** La clave de una línea de propiedades, sin escapes; null si es un comentario o está vacía. */
    private static String propertyKey(String line) {
        String trimmed = line.stripLeading();
        if (trimmed.isEmpty() || isComment(line)) {
            return null;
        }
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '\\' && i + 1 < trimmed.length()) {
                key.append(trimmed.charAt(++i));
            } else if (c == '=' || c == ':' || Character.isWhitespace(c)) {
                break;
            } else {
                key.append(c);
            }
        }
        return key.toString();
    }

    private static boolean isComment(String line) {
        String trimmed = line.stripLeading();
        return trimmed.startsWith("#") || trimmed.startsWith("!");
    }

    /** Un número impar de barras al final une la línea con la siguiente. */
    private static boolean endsWithContinuation(String line) {
        int backslashes = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    /** Escribe en un temporal y lo mueve: si algo falla a medias, el archivo anterior sigue entero. */
    private void writeAtomically(String content) throws IOException {
        Path temp = Files.createTempFile(configFile.getParent(), "config", ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.ISO_8859_1);
            try {
                Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Optional<String> clean(String key) {
        return Optional.ofNullable(key)
                .map(String::strip)
                .filter(k -> !k.isEmpty());
    }
}
