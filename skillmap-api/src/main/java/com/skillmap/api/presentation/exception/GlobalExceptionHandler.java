package com.skillmap.api.presentation.exception;

import com.skillmap.api.domain.exception.CareerGoalNotFoundException;
import com.skillmap.api.domain.exception.SkillNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manejo global de excepciones: ningún controlador necesita try/catch propio.
 * Cualquier excepción de este tipo, venga de donde venga, termina en una
 * respuesta HTTP consistente para los 3 clientes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SkillNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(SkillNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(CareerGoalNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGoalNotFound(CareerGoalNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    public record ErrorResponse(String message) {
    }
}
