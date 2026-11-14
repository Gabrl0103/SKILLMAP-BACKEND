package com.skillmap.api.domain.exception;

/** 409 al registrar un correo ya usado. El mensaje no confirma que exista una cuenta con ese correo. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public static final String MESSAGE =
            "No se pudo crear la cuenta con esos datos. Si ya tienes una cuenta, inicia sesión.";

    public EmailAlreadyRegisteredException() {
        super(MESSAGE);
    }
}
