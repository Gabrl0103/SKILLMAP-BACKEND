package com.skillmap.api.domain.model;

/** Enlace del perfil de un usuario (portfolio, GitHub, LinkedIn…). url siempre es http o https. */
public record ProfileLink(Long id, Long userId, String label, String url) {
}
