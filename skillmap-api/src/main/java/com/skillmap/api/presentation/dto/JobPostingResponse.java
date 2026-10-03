package com.skillmap.api.presentation.dto;

import java.time.Instant;

/** Oferta de empleo para los clientes. Sin la descripción completa, para que el listado pese poco. */
public record JobPostingResponse(
        Long id,
        String source,
        String title,
        String company,
        String location,
        boolean remote,
        String tags,
        String url,
        Instant publishedAt
) {
}
