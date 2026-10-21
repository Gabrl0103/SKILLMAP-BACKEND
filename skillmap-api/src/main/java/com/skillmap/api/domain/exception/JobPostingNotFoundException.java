package com.skillmap.api.domain.exception;

/** La oferta pedida no está guardada (o ya se borró por la retención). El mensaje llega al cliente. */
public class JobPostingNotFoundException extends RuntimeException {

    public static final String MESSAGE = "No se encontró la oferta.";

    public JobPostingNotFoundException() {
        super(MESSAGE);
    }
}
