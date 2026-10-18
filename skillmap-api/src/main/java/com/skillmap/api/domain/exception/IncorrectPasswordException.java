package com.skillmap.api.domain.exception;

/**
 * La contraseña actual no coincide al cambiarla o al eliminar la cuenta. Es un 400, no un 401: la
 * sesión sigue valiendo y el cliente no debe cerrarla.
 */
public class IncorrectPasswordException extends RuntimeException {
    public IncorrectPasswordException() {
        super("La contraseña actual no es correcta.");
    }
}
