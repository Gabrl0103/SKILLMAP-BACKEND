package com.skillmap.api.domain.exception;

/** Fallo genérico del servicio de IA (caído, timeout, key rechazada...). */
public class AiServiceException extends RuntimeException {
    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
