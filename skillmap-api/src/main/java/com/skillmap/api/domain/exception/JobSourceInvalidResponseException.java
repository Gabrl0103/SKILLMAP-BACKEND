package com.skillmap.api.domain.exception;

/** La bolsa de empleo respondió, pero con algo que no se puede interpretar como lista de ofertas. */
public class JobSourceInvalidResponseException extends JobSourceException {
    public JobSourceInvalidResponseException(String message) {
        super(message);
    }

    public JobSourceInvalidResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
