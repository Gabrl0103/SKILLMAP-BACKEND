package com.skillmap.api.domain.model;

import java.time.Instant;

/**
 * Oferta de empleo real traída de una fuente externa (Remotive, Arbeitnow...).
 * La pareja source + externalId la identifica: así la misma oferta no se guarda dos veces.
 *
 * @param description texto plano, ya sin HTML
 * @param tags        etiquetas de la fuente, separadas por comas
 */
public record JobPosting(
        Long id,
        String source,
        String externalId,
        String title,
        String company,
        String location,
        boolean remote,
        String tags,
        String url,
        String description,
        Instant publishedAt,
        Instant fetchedAt
) {

    /** Todo el texto donde se buscan habilidades: título, etiquetas y descripción. */
    public String searchableText() {
        return String.join("\n", nullToEmpty(title), nullToEmpty(tags), nullToEmpty(description));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
