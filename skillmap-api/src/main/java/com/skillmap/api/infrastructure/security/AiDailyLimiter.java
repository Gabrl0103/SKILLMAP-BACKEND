package com.skillmap.api.infrastructure.security;

import com.skillmap.api.domain.exception.AiDailyLimitException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Cupo diario del servicio de IA en la nube: como mucho {@code perIpLimit} consultas por IP y
 * {@code globalLimit} entre todas, para no agotar la cuota de la key del servidor. En memoria:
 * se pierde al reiniciar. Los contadores vuelven a cero al cambiar el día (según el reloj, UTC).
 * Las IP rechazadas por el límite global no se guardan: el mapa nunca pasa de globalLimit entradas.
 */
public class AiDailyLimiter {

    private final int perIpLimit;
    private final int globalLimit;
    private final Clock clock;
    private final Map<String, Integer> perIp = new HashMap<>();
    private LocalDate day;
    private int total;

    public AiDailyLimiter(int perIpLimit, int globalLimit, Clock clock) {
        this.perIpLimit = perIpLimit;
        this.globalLimit = globalLimit;
        this.clock = clock;
    }

    /** Cuenta una consulta de esa IP o lanza {@link AiDailyLimitException} si ya no le quedan. */
    public synchronized void acquire(String ip) {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            perIp.clear();
            total = 0;
        }
        if (perIp.getOrDefault(ip, 0) >= perIpLimit) {
            throw new AiDailyLimitException("Alcanzaste el máximo de " + perIpLimit
                    + " análisis con IA por día. Vuelve a intentarlo mañana.");
        }
        if (total >= globalLimit) {
            throw new AiDailyLimitException(
                    "El servicio de IA alcanzó su límite diario de consultas. Vuelve a intentarlo mañana.");
        }
        perIp.merge(ip, 1, Integer::sum);
        total++;
    }
}
