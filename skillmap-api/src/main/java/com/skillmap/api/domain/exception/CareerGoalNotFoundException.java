package com.skillmap.api.domain.exception;

public class CareerGoalNotFoundException extends RuntimeException {
    public CareerGoalNotFoundException(Long id) {
        super("No se encontró el objetivo con id " + id);
    }
}
