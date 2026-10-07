package com.skillmap.api.domain.exception;

/** Un ajuste recibido no es válido. El mensaje llega al cliente: nunca debe incluir lo recibido. */
public class InvalidSettingsException extends RuntimeException {
    public InvalidSettingsException(String message) {
        super(message);
    }
}
