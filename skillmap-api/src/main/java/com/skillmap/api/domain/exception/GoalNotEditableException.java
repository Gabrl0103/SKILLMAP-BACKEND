package com.skillmap.api.domain.exception;

/** Se intentó editar o borrar un objetivo base (son de todos): 403. */
public class GoalNotEditableException extends RuntimeException {
    public GoalNotEditableException() {
        super("Los objetivos base no se pueden editar ni borrar.");
    }
}
