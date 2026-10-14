package com.skillmap.api.infrastructure.security;

import com.skillmap.api.domain.port.LoginAttemptLimiter;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Como mucho 5 logins fallidos por clave (correo + IP) en 10 minutos. En memoria: se pierde
 * al reiniciar, suficiente para una app de escritorio. Las claves sin fallos recientes se
 * limpian para que el mapa no crezca sin límite.
 */
@Component
public class InMemoryLoginAttemptLimiter implements LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryLoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean isBlocked(String key) {
        Instant now = clock.instant();
        Deque<Instant> recent = failures.computeIfPresent(key, (k, times) -> prune(times, now));
        return recent != null && recent.size() >= MAX_FAILURES;
    }

    @Override
    public void recordFailure(String key) {
        Instant now = clock.instant();
        if (failures.size() > CLEANUP_THRESHOLD) {
            failures.keySet().forEach(k -> failures.computeIfPresent(k, (kk, times) -> prune(times, now)));
        }
        failures.compute(key, (k, times) -> {
            Deque<Instant> recent = times == null ? null : prune(times, now);
            if (recent == null) {
                recent = new ArrayDeque<>();
            }
            recent.addLast(now);
            return recent;
        });
    }

    @Override
    public void reset(String key) {
        failures.remove(key);
    }

    /** Quita los fallos fuera de la ventana; devuelve null si no queda ninguno (borra la clave). */
    private static Deque<Instant> prune(Deque<Instant> times, Instant now) {
        Instant limit = now.minus(WINDOW);
        while (!times.isEmpty() && !times.peekFirst().isAfter(limit)) {
            times.pollFirst();
        }
        return times.isEmpty() ? null : times;
    }
}
