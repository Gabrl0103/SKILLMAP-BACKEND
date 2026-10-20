package com.skillmap.api.infrastructure.desktop;

import com.skillmap.api.infrastructure.config.CloudConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Optional;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Una sola instancia por base de datos (H2 en modo archivo no admite dos procesos).
 * Corre antes de crear el contexto: si otra instancia ya tiene el cerrojo, abre el
 * navegador en su puerto y termina sin levantar nada. Usa un cerrojo propio en vez
 * de probar a abrir H2 porque la primera instancia lo toma desde el primer momento,
 * antes de abrir la base (cubre el doble clic rápido).
 * Lo registra {@code main}, así que no actúa en los tests.
 */
public class SingleInstanceGuard implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SingleInstanceGuard.class);
    /** Lo que puede tardar la otra instancia en terminar de arrancar y publicar su puerto. */
    private static final Duration PORT_WAIT = Duration.ofSeconds(60);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);

    /**
     * Se mantiene abierto durante toda la vida del proceso; el sistema operativo suelta
     * el cerrojo al terminar, también si se mata con taskkill /F.
     */
    private static FileChannel lockChannel;

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        // En la nube no hay base en archivo ni navegador que abrir.
        if (environment.matchesProfiles(CloudConfig.PROFILE)) {
            return;
        }
        InstanceFiles files = InstanceFiles.from(environment);
        if (acquireLock(files)) {
            // El de una ejecución anterior cerrada a la fuerza ya no vale.
            files.deletePort();
            return;
        }

        int port = waitForPort(files).orElseGet(() -> {
            int configured = environment.getProperty("server.port", Integer.class, 8080);
            log.warn("La instancia abierta no publicó su puerto; se prueba con {}", configured);
            return configured;
        });
        log.info("Cenit ya está abierta (puerto {}): se abre su ventana y este arranque termina", port);
        if (environment.getProperty(DesktopLauncher.OPEN_BROWSER_PROPERTY, Boolean.class, true)) {
            BrowserOpener.open(port);
        }
        System.exit(0);
    }

    /** Debe correr antes que PortSelector: si hay otra instancia no tiene sentido buscar puerto. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    private static synchronized boolean acquireLock(InstanceFiles files) {
        if (lockChannel != null) {
            return true;
        }
        try {
            Files.createDirectories(files.dataDir());
            FileChannel channel = FileChannel.open(files.lockFile(), CREATE, WRITE);
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                return false;
            }
            lockChannel = channel;
            return true;
        } catch (IOException e) {
            // Si no se puede comprobar se arranca igual: en el peor caso H2 rechazará la segunda.
            log.warn("No se pudo comprobar si Cenit ya está abierta ({}); se arranca igualmente", e.getMessage());
            return true;
        }
    }

    private static Optional<Integer> waitForPort(InstanceFiles files) {
        long deadline = System.nanoTime() + PORT_WAIT.toNanos();
        while (true) {
            Optional<Integer> port = files.readPort();
            if (port.isPresent() || System.nanoTime() > deadline) {
                return port;
            }
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
    }
}
