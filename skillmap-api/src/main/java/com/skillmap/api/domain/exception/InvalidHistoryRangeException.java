package com.skillmap.api.domain.exception;

/** El rango de días pedido para el historial está fuera de los límites. El mensaje llega al cliente. */
public class InvalidHistoryRangeException extends RuntimeException {
    public InvalidHistoryRangeException(int maxDays) {
        super("El parámetro 'days' debe estar entre 1 y " + maxDays + ".");
    }
}
