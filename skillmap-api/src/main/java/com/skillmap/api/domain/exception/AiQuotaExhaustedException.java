package com.skillmap.api.domain.exception;

/** El proveedor de IA agotó el cupo diario gratuito (HTTP 429 que no se arregla esperando unos segundos). */
public class AiQuotaExhaustedException extends AiRateLimitException {

    public AiQuotaExhaustedException(String message) {
        super(message);
    }
}
