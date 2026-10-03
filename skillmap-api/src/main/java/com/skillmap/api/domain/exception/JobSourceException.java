package com.skillmap.api.domain.exception;

/** Fallo al traer ofertas de una bolsa de empleo externa. */
public class JobSourceException extends RuntimeException {
    public JobSourceException(String message) {
        super(message);
    }

    public JobSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
