package com.skillmap.api.domain.exception;

/** La IA respondió, pero con algo que no se puede interpretar como lista de habilidades. */
public class AiInvalidResponseException extends AiServiceException {
    public AiInvalidResponseException(String message) {
        super(message);
    }

    public AiInvalidResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
