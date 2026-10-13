package com.skillmap.api.domain.exception;

public class AuthenticationRequiredException extends RuntimeException {

    public static final String MESSAGE = "Sesión no válida o expirada. Inicia sesión de nuevo.";

    public AuthenticationRequiredException() {
        super(MESSAGE);
    }
}
