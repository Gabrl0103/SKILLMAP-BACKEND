package com.skillmap.api.domain.port;

/** Limita los logins fallidos por clave (correo + IP) dentro de una ventana de tiempo. */
public interface LoginAttemptLimiter {
    boolean isBlocked(String key);
    void recordFailure(String key);
    void reset(String key);
}
