package com.skillmap.api.infrastructure.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Abre la interfaz: Edge en modo app (ventana propia, sin barra del navegador) y, si no se
 * puede, el navegador por defecto. No usa java.awt.Desktop: Spring Boot arranca en modo headless.
 */
final class BrowserOpener {

    private static final Logger log = LoggerFactory.getLogger(BrowserOpener.class);
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    private BrowserOpener() {
    }

    static void open(int port) {
        String url = "http://localhost:" + port;
        if (WINDOWS) {
            for (String edge : edgeCandidates()) {
                if (start(edge, "--app=" + url)) {
                    return;
                }
            }
            if (start("rundll32", "url.dll,FileProtocolHandler", url)) {
                return;
            }
        } else if (start(MAC ? "open" : "xdg-open", url)) {
            return;
        }
        log.warn("No se pudo abrir el navegador. Abre {} a mano.", url);
    }

    /** "msedge" a secas no suele estar en el PATH: primero las rutas de instalación habituales. */
    private static List<String> edgeCandidates() {
        List<String> candidates = new ArrayList<>(Stream.of("ProgramFiles(x86)", "ProgramFiles", "LOCALAPPDATA")
                .map(System::getenv)
                .filter(Objects::nonNull)
                .map(dir -> Path.of(dir, "Microsoft", "Edge", "Application", "msedge.exe"))
                .filter(Files::isRegularFile)
                .map(Path::toString)
                .toList());
        candidates.add("msedge");
        return candidates;
    }

    private static boolean start(String... command) {
        try {
            new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return true;
        } catch (IOException e) {
            log.debug("No se pudo ejecutar {}: {}", command[0], e.getMessage());
            return false;
        }
    }
}
