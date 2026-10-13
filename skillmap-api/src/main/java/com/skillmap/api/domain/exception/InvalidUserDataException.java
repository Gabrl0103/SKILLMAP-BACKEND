package com.skillmap.api.domain.exception;

/** Nombre, correo o contraseña no cumplen las reglas. El mensaje nunca incluye el valor recibido. */
public class InvalidUserDataException extends RuntimeException {
    public InvalidUserDataException(String message) {
        super(message);
    }
}
