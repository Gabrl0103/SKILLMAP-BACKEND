package com.skillmap.api.domain.model;

import java.time.Instant;

/**
 * Resultado de sincronizar una fuente.
 *
 * @param lastSyncAt última sincronización correcta de la fuente (null si nunca la hubo)
 */
public record SourceSyncResult(
        String source,
        SourceSyncStatus status,
        int fetched,
        int added,
        Instant lastSyncAt,
        String message
) {
}
