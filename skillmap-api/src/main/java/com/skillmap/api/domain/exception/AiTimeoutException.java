package com.skillmap.api.domain.exception;

/** El proveedor de IA no terminó de responder dentro del tiempo máximo. */
public class AiTimeoutException extends AiServiceException {

    public AiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
