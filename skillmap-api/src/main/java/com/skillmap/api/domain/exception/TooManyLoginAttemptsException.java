package com.skillmap.api.domain.exception;

public class TooManyLoginAttemptsException extends RuntimeException {
    public TooManyLoginAttemptsException() {
        super("Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo.");
    }
}
