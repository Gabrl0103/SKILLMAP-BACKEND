package com.skillmap.api.domain.exception;

import java.time.Duration;

/** 429: demasiados intentos fallidos. retryAfter es lo que falta para poder intentarlo de nuevo. */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super(message(retryAfter));
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

    /** En minutos enteros hacia arriba (nunca "0 minutos"). */
    public static long minutes(Duration retryAfter) {
        long seconds = Math.max(1, retryAfter.toSeconds());
        return (seconds + 59) / 60;
    }

    private static String message(Duration retryAfter) {
        long minutes = minutes(retryAfter);
        return "Demasiados intentos fallidos. Inténtalo de nuevo en " + minutes
                + (minutes == 1 ? " minuto." : " minutos.");
    }
}
