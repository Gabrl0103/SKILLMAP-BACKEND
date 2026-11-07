package com.skillmap.api.infrastructure.desktop;

import com.skillmap.api.infrastructure.config.CloudConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.io.InputStream;

/**
 * Con {@value #PROPERTY}=true, la app termina cuando se cierra su entrada estándar. La activa la app
 * de escritorio (Electron), que lanza el backend con stdin conectado a una tubería:
 * - Al cerrar la ventana, Electron cierra la tubería y el backend se apaga de forma ordenada
 *   (System.exit corre el cierre de Spring: H2 guarda y suelta la base).
 * - Si Electron muere de golpe, el sistema cierra la tubería y el backend no queda huérfano.
 * Desactivada por defecto: el ejecutable de jpackage no tiene stdin y terminaría al instante.
 * Lo registra {@code main}, así que no actúa en los tests.
 */
public class StdinLifeline implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    static final String PROPERTY = "skillmap.desktop.exit-on-stdin-close";
    private static final Logger log = LoggerFactory.getLogger(StdinLifeline.class);

    private final InputStream input;
    private final Runnable exit;
    private boolean started;

    public StdinLifeline() {
        this(System.in, () -> System.exit(0));
    }

    /** Permite a los tests dar otra entrada y comprobar la salida sin terminar la JVM. */
    StdinLifeline(InputStream input, Runnable exit) {
        this.input = input;
        this.exit = exit;
    }

    @Override
    public synchronized void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        if (started || event.getEnvironment().matchesProfiles(CloudConfig.PROFILE)
                || !event.getEnvironment().getProperty(PROPERTY, Boolean.class, false)) {
            return;
        }
        started = true;
        Thread watcher = new Thread(this::watch, "stdin-lifeline");
        watcher.setDaemon(true);
        watcher.start();
    }

    /** Antes que SingleInstanceGuard: si Electron se cierra mientras se espera, también se sale. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 2;
    }

    void watch() {
        try {
            byte[] buffer = new byte[256];
            while (input.read(buffer) >= 0) {
                // Lo que llegue se descarta: solo importa el cierre.
            }
        } catch (IOException e) {
            log.debug("stdin dejó de estar disponible: {}", e.getMessage());
        }
        log.info("Se cerró la app de escritorio (stdin): Cenit se detiene");
        exit.run();
    }
}
