package com.skillmap.api.presentation.dto;

import jakarta.validation.constraints.Size;

/** Cuerpo de POST /api/analyzer/analyze. toString no muestra la oferta (Spring registra el cuerpo leído en DEBUG). */
public record AnalyzeJobRequest(
        @Size(max = 20_000, message = "La descripción de la oferta supera el máximo de 8.000 caracteres.")
        String jobDescription) {

    @Override
    public String toString() {
        return "AnalyzeJobRequest[jobDescription=" + (jobDescription == null ? "null"
                : "(" + jobDescription.length() + " caracteres)") + "]";
    }
}
