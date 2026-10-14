package com.skillmap.api.domain.exception;

/** No se pudieron leer o guardar los ajustes. El mensaje nunca incluye su contenido. */
public class SettingsStorageException extends RuntimeException {
    public SettingsStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
