package com.skillmap.api.domain.model;

import java.time.Instant;

/**
 * Resultado de sincronizar una fuente.
 *
 * @param lastSyncAt           última sincronización correcta de la fuente (null si nunca la hubo)
 * @param nextSyncAt           desde cuándo se puede volver a consultar la fuente (null si ya se puede:
 *                             nunca se sincronizó con éxito)
 * @param minutesUntilNextSync minutos que faltan para nextSyncAt, redondeando hacia arriba; 0 si ya se puede
 */
public record SourceSyncResult(
        String source,
        SourceSyncStatus status,
        int fetched,
        int added,
        Instant lastSyncAt,
        Instant nextSyncAt,
        long minutesUntilNextSync,
        String message
) {
}
