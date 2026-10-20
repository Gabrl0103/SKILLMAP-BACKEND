package com.skillmap.api.infrastructure.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Cuando la app está lista (datos sembrados incluidos): publica el puerto para que otro
 * arranque abra esta misma instancia y, si está activado, abre la interfaz.
 */
@Component
public class DesktopLauncher {

    static final String OPEN_BROWSER_PROPERTY = "skillmap.desktop.open-browser";
    private static final Logger log = LoggerFactory.getLogger(DesktopLauncher.class);

    private final boolean openBrowser;
    private final InstanceFiles files;
    private boolean portPublished;

    public DesktopLauncher(@Value("${" + OPEN_BROWSER_PROPERTY + ":true}") boolean openBrowser,
                           Environment environment) {
        this.openBrowser = openBrowser;
        this.files = InstanceFiles.from(environment);
    }

    @EventListener
    public void onReady(ApplicationReadyEvent event) {
        // Sin servidor real (tests con entorno simulado) no hay puerto que publicar ni abrir.
        if (!(event.getApplicationContext() instanceof WebServerApplicationContext web)) {
            return;
        }
        int port = web.getWebServer().getPort();
        files.writePort(port);
        portPublished = true;
        log.info("Cenit lista en http://localhost:{}", port);
        if (openBrowser) {
            BrowserOpener.open(port);
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void onClose() {
        if (portPublished) {
            files.deletePort();
        }
    }
}
