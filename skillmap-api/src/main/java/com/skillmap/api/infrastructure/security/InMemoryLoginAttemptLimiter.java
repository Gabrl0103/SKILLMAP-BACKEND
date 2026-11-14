package com.skillmap.api.infrastructure.security;

import com.skillmap.api.domain.port.LoginAttemptLimiter;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueo temporal creciente, en memoria (se pierde al reiniciar: suficiente para una app de escritorio local).
 * <ul>
 *   <li>Por correo: al 5.º fallo seguido, 15 minutos de bloqueo; cada bloqueo nuevo dobla el anterior (15, 30, 60...)
 *       hasta 24 horas. Un acierto lo pone todo a cero.</li>
 *   <li>Por IP (tope para quien prueba muchos correos): al 20.º fallo, el mismo bloqueo creciente. Un acierto no lo
 *       limpia.</li>
 *   <li>Los fallos sin bloqueo se olvidan tras 1 hora sin fallar; los bloqueos anteriores, tras 24 horas.</li>
 * </ul>
 * Las claves olvidadas se limpian para que los mapas no crezcan sin límite.
 */
@Component
public class InMemoryLoginAttemptLimiter implements LoginAttemptLimiter {

    static final int MAX_FAILURES_PER_EMAIL = 5;
    static final int MAX_FAILURES_PER_IP = 20;
    static final Duration BASE_LOCK = Duration.ofMinutes(15);
    static final Duration MAX_LOCK = Duration.ofHours(24);
    static final Duration FORGET_FAILURES = Duration.ofHours(1);
    static final Duration FORGET_LOCKOUTS = Duration.ofHours(24);
    private static final int CLEANUP_THRESHOLD = 10_000;

    /** failures: fallos desde el último bloqueo; lockouts: bloqueos recientes (marcan la duración del siguiente). */
    record Attempts(int failures, int lockouts, Instant lastFailure, Instant lockedUntil) {

        static final Attempts NONE = new Attempts(0, 0, Instant.MIN, Instant.MIN);

        Attempts forgetOld(Instant now) {
            boolean locked = lockedUntil.isAfter(now);
            int keptFailures = !locked && lastFailure.plus(FORGET_FAILURES).isBefore(now) ? 0 : failures;
            int keptLockouts = !locked && lastFailure.plus(FORGET_LOCKOUTS).isBefore(now) ? 0 : lockouts;
            return new Attempts(keptFailures, keptLockouts, lastFailure, lockedUntil);
        }

        boolean forgotten(Instant now) {
            Attempts current = forgetOld(now);
            return current.failures == 0 && current.lockouts == 0 && !lockedUntil.isAfter(now);
        }

        Attempts withFailure(Instant now, int maxFailures) {
            Attempts current = forgetOld(now);
            int newFailures = current.failures + 1;
            if (newFailures < maxFailures) {
                return new Attempts(newFailures, current.lockouts, now, current.lockedUntil);
            }
            int newLockouts = current.lockouts + 1;
            return new Attempts(0, newLockouts, now, now.plus(lockDuration(newLockouts)));
        }

        Optional<Duration> remaining(Instant now) {
            return lockedUntil.isAfter(now) ? Optional.of(Duration.between(now, lockedUntil)) : Optional.empty();
        }
    }

    private final Map<String, Attempts> byEmail = new ConcurrentHashMap<>();
    private final Map<String, Attempts> byIp = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryLoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** 15 min, 30, 60, 120... hasta 24 h. */
    static Duration lockDuration(int lockouts) {
        int doublings = Math.min(Math.max(lockouts - 1, 0), 10);
        Duration lock = BASE_LOCK.multipliedBy(1L << doublings);
        return lock.compareTo(MAX_LOCK) > 0 ? MAX_LOCK : lock;
    }

    @Override
    public Optional<Duration> blockedFor(String email, String clientIp) {
        Instant now = clock.instant();
        Optional<Duration> emailLock = remaining(byEmail, email, now);
        Optional<Duration> ipLock = remaining(byIp, clientIp, now);
        if (emailLock.isPresent() && ipLock.isPresent()) {
            return Optional.of(emailLock.get().compareTo(ipLock.get()) >= 0 ? emailLock.get() : ipLock.get());
        }
        return emailLock.isPresent() ? emailLock : ipLock;
    }

    @Override
    public void recordFailure(String email, String clientIp) {
        Instant now = clock.instant();
        cleanup(now);
        if (email != null) {
            byEmail.compute(email, (k, a) -> (a == null ? Attempts.NONE : a).withFailure(now, MAX_FAILURES_PER_EMAIL));
        }
        if (clientIp != null) {
            byIp.compute(clientIp, (k, a) -> (a == null ? Attempts.NONE : a).withFailure(now, MAX_FAILURES_PER_IP));
        }
    }

    @Override
    public void reset(String email) {
        if (email != null) {
            byEmail.remove(email);
        }
    }

    private static Optional<Duration> remaining(Map<String, Attempts> map, String key, Instant now) {
        if (key == null) {
            return Optional.empty();
        }
        Attempts attempts = map.get(key);
        return attempts == null ? Optional.empty() : attempts.remaining(now);
    }

    private void cleanup(Instant now) {
        if (byEmail.size() > CLEANUP_THRESHOLD) {
            byEmail.entrySet().removeIf(e -> e.getValue().forgotten(now));
        }
        if (byIp.size() > CLEANUP_THRESHOLD) {
            byIp.entrySet().removeIf(e -> e.getValue().forgotten(now));
        }
    }
}
