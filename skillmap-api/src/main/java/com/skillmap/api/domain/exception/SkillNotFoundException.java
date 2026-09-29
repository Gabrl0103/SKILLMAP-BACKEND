package com.skillmap.api.domain.exception;

public class SkillNotFoundException extends RuntimeException {
    public SkillNotFoundException(Long id) {
        super("No se encontró la habilidad con id " + id);
    }
}
