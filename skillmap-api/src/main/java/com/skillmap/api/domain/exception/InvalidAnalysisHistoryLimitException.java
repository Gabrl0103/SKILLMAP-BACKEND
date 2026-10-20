package com.skillmap.api.domain.exception;

/** El número de análisis pedido al historial está fuera de los límites. El mensaje llega al cliente. */
public class InvalidAnalysisHistoryLimitException extends RuntimeException {
    public InvalidAnalysisHistoryLimitException(int max) {
        super("El parámetro 'limit' debe estar entre 1 y " + max + ".");
    }
}
