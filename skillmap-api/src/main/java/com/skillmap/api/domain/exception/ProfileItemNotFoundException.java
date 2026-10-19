package com.skillmap.api.domain.exception;

/** El enlace o proyecto no existe o es de otro usuario: las dos cosas responden igual (404). */
public class ProfileItemNotFoundException extends RuntimeException {

    public static final String LINK = "No se encontró el enlace.";
    public static final String PROJECT = "No se encontró el proyecto.";

    public ProfileItemNotFoundException(String message) {
        super(message);
    }
}
