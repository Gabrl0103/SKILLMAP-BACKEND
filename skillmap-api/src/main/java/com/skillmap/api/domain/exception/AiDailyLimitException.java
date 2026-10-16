package com.skillmap.api.domain.exception;

/** Se pasó el máximo diario de consultas al servicio de IA en la nube (por IP o global). */
public class AiDailyLimitException extends RuntimeException {
    public AiDailyLimitException(String message) {
        super(message);
    }
}
