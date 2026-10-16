package com.skillmap.api.presentation.dto;

/** Cuerpo de POST /api/analyzer/analyze. toString no muestra la oferta (Spring registra el cuerpo leído en DEBUG). */
public record AnalyzeJobRequest(String jobDescription) {

    @Override
    public String toString() {
        return "AnalyzeJobRequest[jobDescription=" + (jobDescription == null ? "null"
                : "(" + jobDescription.length() + " caracteres)") + "]";
    }
}
