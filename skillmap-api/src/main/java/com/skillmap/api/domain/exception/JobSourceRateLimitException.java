package com.skillmap.api.domain.exception;

/** La bolsa de empleo rechazó la petición por límite de uso (HTTP 429). */
public class JobSourceRateLimitException extends JobSourceException {
    public JobSourceRateLimitException(String message) {
        super(message);
    }
}
