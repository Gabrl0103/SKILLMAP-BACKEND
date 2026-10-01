package com.skillmap.api.domain.exception;

/** El proveedor de IA rechazó la petición por límite de uso (HTTP 429). */
public class AiRateLimitException extends AiServiceException {
    public AiRateLimitException(String message) {
        super(message);
    }
}
