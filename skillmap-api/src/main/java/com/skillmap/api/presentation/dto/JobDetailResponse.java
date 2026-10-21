package com.skillmap.api.presentation.dto;

import java.time.Instant;

/** Una oferta con su descripción completa (texto plano), para llevarla al Analizador. */
public record JobDetailResponse(
        Long id,
        String source,
        String title,
        String company,
        String location,
        boolean remote,
        String url,
        Instant publishedAt,
        String description
) {
}
