package com.skillmap.api.domain.exception;

/** El análisis no existe o es de otro usuario: las dos cosas responden igual (404). */
public class AnalysisHistoryNotFoundException extends RuntimeException {

    public static final String MESSAGE = "No se encontró el análisis.";

    public AnalysisHistoryNotFoundException() {
        super(MESSAGE);
    }
}
