package com.skillmap.api.presentation.dto;

/** POST y PUT /api/profile/links: etiqueta (1 a 40 caracteres) y URL http o https. */
public record ProfileLinkRequest(String label, String url) {
}
