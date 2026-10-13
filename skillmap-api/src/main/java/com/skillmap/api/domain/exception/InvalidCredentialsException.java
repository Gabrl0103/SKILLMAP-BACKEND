package com.skillmap.api.domain.exception;

/** Mismo mensaje si el correo no existe o si la contraseña no coincide: no revela qué cuentas hay. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Correo o contraseña incorrectos");
    }
}
