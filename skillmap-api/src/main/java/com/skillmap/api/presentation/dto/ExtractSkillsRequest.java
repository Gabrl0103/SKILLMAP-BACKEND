package com.skillmap.api.presentation.dto;

/** POST /api/ai/extract-skills (perfil cloud). toString no muestra la oferta (Spring registra el cuerpo leído en DEBUG). */
public record ExtractSkillsRequest(String jobDescription) {

    @Override
    public String toString() {
        return "ExtractSkillsRequest[jobDescription=" + (jobDescription == null ? "null"
                : "(" + jobDescription.length() + " caracteres)") + "]";
    }
}
