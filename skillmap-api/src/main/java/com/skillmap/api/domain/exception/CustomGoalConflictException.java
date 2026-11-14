package com.skillmap.api.domain.exception;

/** El nombre ya lo usa otro objetivo (base o propio) o se llegó al máximo de objetivos propios: 409. */
public class CustomGoalConflictException extends RuntimeException {
    public CustomGoalConflictException(String message) {
        super(message);
    }
}
