package com.skillmap.api.domain.exception;

/** La bolsa de empleo no responde: caída, error 5xx, sin conexión o tiempo agotado. */
public class JobSourceUnavailableException extends JobSourceException {
    public JobSourceUnavailableException(String message) {
        super(message);
    }

    public JobSourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
