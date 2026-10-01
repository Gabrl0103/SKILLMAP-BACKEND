package com.skillmap.api.domain.exception;

/** No hay API key configurada para el servicio de IA. */
public class AiNotConfiguredException extends AiServiceException {
    public AiNotConfiguredException(String message) {
        super(message);
    }
}
