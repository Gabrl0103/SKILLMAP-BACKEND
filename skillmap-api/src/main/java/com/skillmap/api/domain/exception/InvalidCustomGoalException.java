package com.skillmap.api.domain.exception;

/** Un objetivo propio no cumple las reglas (CustomGoalFactory): 400. Nunca repite lo recibido. */
public class InvalidCustomGoalException extends RuntimeException {
    public InvalidCustomGoalException(String message) {
        super(message);
    }
}
