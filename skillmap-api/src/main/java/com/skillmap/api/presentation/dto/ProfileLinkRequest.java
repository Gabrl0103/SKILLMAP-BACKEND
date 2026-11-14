package com.skillmap.api.presentation.dto;

import com.skillmap.api.presentation.validation.NoControlChars;
import jakarta.validation.constraints.Size;

/** POST y PUT /api/profile/links: etiqueta (1 a 40 caracteres) y URL http o https. */
public record ProfileLinkRequest(
        @Size(max = 200, message = "La etiqueta del enlace debe tener entre 1 y 40 caracteres.")
        @NoControlChars
        String label,
        @Size(max = 2048, message = "La URL puede tener como máximo 2048 caracteres.")
        @NoControlChars(message = "La URL debe empezar por http:// o https:// (por ejemplo, https://mi-portfolio.com).")
        String url) {
}
