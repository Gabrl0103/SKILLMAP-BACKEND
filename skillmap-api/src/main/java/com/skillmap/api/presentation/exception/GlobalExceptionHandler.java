package com.skillmap.api.presentation.exception;

import com.skillmap.api.domain.exception.AiInvalidResponseException;
import com.skillmap.api.domain.exception.AiNotConfiguredException;
import com.skillmap.api.domain.exception.AiRateLimitException;
import com.skillmap.api.domain.exception.AiServiceException;
import com.skillmap.api.domain.exception.AiTimeoutException;
import com.skillmap.api.domain.exception.CareerGoalNotFoundException;
import com.skillmap.api.domain.exception.InvalidJobDescriptionException;
import com.skillmap.api.domain.exception.JobSourceException;
import com.skillmap.api.domain.exception.JobSourceInvalidResponseException;
import com.skillmap.api.domain.exception.JobSourceRateLimitException;
import com.skillmap.api.domain.exception.JobSourceUnavailableException;
import com.skillmap.api.domain.exception.SkillNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Manejo global de excepciones: ningún controlador necesita try/catch propio.
 * Cualquier excepción de este tipo, venga de donde venga, termina en una
 * respuesta HTTP consistente para los 3 clientes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(InvalidJobDescriptionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJobDescription(InvalidJobDescriptionException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "El cuerpo de la petición no es un JSON válido."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "El parámetro '" + ex.getName() + "' no tiene un valor válido."));
    }

    @ExceptionHandler(AiNotConfiguredException.class)
    public ResponseEntity<ErrorResponse> handleAiNotConfigured(AiNotConfiguredException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(AiRateLimitException.class)
    public ResponseEntity<ErrorResponse> handleAiRateLimit(AiRateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(AiTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleAiTimeout(AiTimeoutException ex) {
        log.warn("La IA tardó demasiado: {}", ex.getMessage(), ex.getCause());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(AiInvalidResponseException.class)
    public ResponseEntity<ErrorResponse> handleAiInvalidResponse(AiInvalidResponseException ex) {
        log.warn("Respuesta inválida de la IA: {}", ex.getMessage(), ex.getCause());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ErrorResponse> handleAiService(AiServiceException ex) {
        log.warn("Fallo del servicio de IA: {}", ex.getMessage(), ex.getCause());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(JobSourceRateLimitException.class)
    public ResponseEntity<ErrorResponse> handleJobSourceRateLimit(JobSourceRateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(JobSourceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleJobSourceUnavailable(JobSourceUnavailableException ex) {
        log.warn("Fuente de ofertas caída: {}", ex.getMessage(), ex.getCause());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(JobSourceInvalidResponseException.class)
    public ResponseEntity<ErrorResponse> handleJobSourceInvalidResponse(JobSourceInvalidResponseException ex) {
        log.warn("Respuesta inválida de una fuente de ofertas: {}", ex.getMessage(), ex.getCause());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(JobSourceException.class)
    public ResponseEntity<ErrorResponse> handleJobSource(JobSourceException ex) {
        log.warn("Fallo de una fuente de ofertas: {}", ex.getMessage(), ex.getCause());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(ex.getMessage()));
    }

    public record ErrorResponse(String message) {
    }
}
