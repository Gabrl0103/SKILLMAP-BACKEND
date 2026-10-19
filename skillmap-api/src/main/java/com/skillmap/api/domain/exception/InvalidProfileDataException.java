package com.skillmap.api.domain.exception;

/** Un enlace o un proyecto del perfil no cumple las reglas, o se superó el máximo. Nunca repite lo recibido. */
public class InvalidProfileDataException extends RuntimeException {
    public InvalidProfileDataException(String message) {
        super(message);
    }
}
