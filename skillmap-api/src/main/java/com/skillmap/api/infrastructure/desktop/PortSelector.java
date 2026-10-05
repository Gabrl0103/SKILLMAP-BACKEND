package com.skillmap.api.infrastructure.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * Si server.port está ocupado, prueba los siguientes y, si tampoco, deja que el sistema
 * elija uno libre (server.port=0). Se comprueba en server.address, la misma dirección en la
 * que escuchará Tomcat. Se prefieren los cercanos porque el origen del frontend
 * (y con él su localStorage) incluye el puerto. DesktopLauncher abre el navegador en el
 * puerto que se use al final. Lo registra {@code main}, así que no actúa en los tests.
 */
public class PortSelector implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final Logger log = LoggerFactory.getLogger(PortSelector.class);
    static final int ATTEMPTS = 10;
    private static final int MAX_PORT = 65535;

    private final PortProbe probe;

    public PortSelector() {
        this(PortSelector::canBind);
    }

    /** Permite a los tests decidir qué puertos están libres. */
    PortSelector(PortProbe probe) {
        this.probe = probe;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        selectPort(event.getEnvironment());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    void selectPort(ConfigurableEnvironment environment) {
        int preferred = environment.getProperty("server.port", Integer.class, 8080);
        if (preferred <= 0) {
            return; // 0: ya es uno libre al azar; negativo: sin servidor web
        }
        InetAddress address = bindAddress(environment.getProperty("server.address"));
        int chosen = 0;
        for (int port = preferred; port < preferred + ATTEMPTS && port <= MAX_PORT; port++) {
            if (probe.isFree(address, port)) {
                chosen = port;
                break;
            }
        }
        if (chosen == preferred) {
            return;
        }
        log.info("El puerto {} está ocupado: se usa {}", preferred, chosen == 0 ? "uno libre al azar" : chosen);
        environment.getPropertySources().addFirst(new MapPropertySource("skillmapFreePort", Map.of("server.port", chosen)));
    }

    /** null = todas las interfaces, como hace Tomcat sin server.address. */
    private static InetAddress bindAddress(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        try {
            return InetAddress.getByName(configured.strip());
        } catch (UnknownHostException e) {
            log.warn("server.address no es válida ({}); se comprueba el puerto en todas las interfaces", configured);
            return null;
        }
    }

    private static boolean canBind(InetAddress address, int port) {
        try (ServerSocket ignored = new ServerSocket(port, 0, address)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @FunctionalInterface
    interface PortProbe {
        boolean isFree(InetAddress address, int port);
    }
}
