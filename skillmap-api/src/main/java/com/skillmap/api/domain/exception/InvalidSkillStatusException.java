package com.skillmap.api.domain.exception;

/** El estado recibido para una habilidad no existe o falta. El mensaje llega al cliente: nunca incluye lo recibido. */
public class InvalidSkillStatusException extends RuntimeException {
    public InvalidSkillStatusException() {
        super("El estado no es válido. Valores permitidos: PENDING, IN_PROGRESS, MASTERED.");
    }
}
