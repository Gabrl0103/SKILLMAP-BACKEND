package com.skillmap.api.domain.exception;

/** El número de ofertas recomendadas pedido está fuera de los límites. El mensaje llega al cliente. */
public class InvalidRecommendationLimitException extends RuntimeException {
    public InvalidRecommendationLimitException(int max) {
        super("El parámetro 'limit' debe estar entre 1 y " + max + ".");
    }
}
