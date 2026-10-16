package com.skillmap.api.domain.service;

import com.skillmap.api.domain.exception.InvalidJobDescriptionException;

/** Reglas del texto de una oferta que se manda a analizar: lo comparten el analizador y el servicio en la nube. */
public final class JobDescriptionValidator {

    public static final int MAX_LENGTH = 8_000;

    private JobDescriptionValidator() {
    }

    public static void validate(String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) {
            throw new InvalidJobDescriptionException("La descripción de la oferta no puede estar vacía.");
        }
        if (jobDescription.strip().length() > MAX_LENGTH) {
            throw new InvalidJobDescriptionException(
                    "La descripción de la oferta supera el máximo de 8.000 caracteres.");
        }
    }
}
