package com.skillmap.api.infrastructure.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Archivos con los que se coordinan las instancias de la app, junto a la base de datos:
 * así dos instancias con la misma base se excluyen y con bases distintas no.
 * - instance.lock: cerrojo del sistema operativo mientras la app corre.
 * - instance.port: puerto en el que escucha la instancia abierta.
 */
final class InstanceFiles {

    private static final Logger log = LoggerFactory.getLogger(InstanceFiles.class);

    private final Path dataDir;

    InstanceFiles(Path dataDir) {
        this.dataDir = dataDir.toAbsolutePath();
    }

    static InstanceFiles from(Environment environment) {
        return new InstanceFiles(Path.of(environment.getRequiredProperty("skillmap.data-dir")));
    }

    Path dataDir() {
        return dataDir;
    }

    Path lockFile() {
        return dataDir.resolve("instance.lock");
    }

    Path portFile() {
        return dataDir.resolve("instance.port");
    }

    void writePort(int port) {
        try {
            Files.createDirectories(dataDir);
            Files.writeString(portFile(), Integer.toString(port), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            log.warn("No se pudo guardar el puerto en {}: {}", portFile(), e.getMessage());
        }
    }

    /** Vacío si no existe (la instancia aún arranca) o no se puede leer. */
    Optional<Integer> readPort() {
        try {
            return Optional.of(Integer.parseInt(Files.readString(portFile(), StandardCharsets.US_ASCII).strip()));
        } catch (IOException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    void deletePort() {
        try {
            Files.deleteIfExists(portFile());
        } catch (IOException e) {
            log.warn("No se pudo borrar {}: {}", portFile(), e.getMessage());
        }
    }
}
