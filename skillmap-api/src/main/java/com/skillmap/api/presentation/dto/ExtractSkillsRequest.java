package com.skillmap.api.presentation.dto;

import jakarta.validation.constraints.Size;

/** POST /api/ai/extract-skills (perfil cloud). toString no muestra la oferta (Spring registra el cuerpo leído en DEBUG). */
public record ExtractSkillsRequest(
        @Size(max = 20_000, message = "La descripción de la oferta supera el máximo de 8.000 caracteres.")
        String jobDescription) {

    @Override
    public String toString() {
        return "ExtractSkillsRequest[jobDescription=" + (jobDescription == null ? "null"
                : "(" + jobDescription.length() + " caracteres)") + "]";
    }
}
