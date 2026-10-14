package com.skillmap.api.presentation.dto;

import java.time.Instant;
import java.util.List;

/** Resumen de POST /api/jobs/sync: qué pasó con cada fuente y si se recalculó la demanda. */
public record JobSyncResponse(
        List<SourceResult> sources,
        int totalJobs,
        boolean demandRecalculated
) {

    /**
     * status: SYNCED, SKIPPED (sincronizada hace poco) o FAILED.
     * updated: true si en esta llamada se trajeron ofertas de la fuente (status SYNCED).
     * nextSyncAt / minutesUntilNextSync: cuándo se podrá volver a sincronizar y cuántos minutos
     * faltan (0 = ya se puede; nextSyncAt null si nunca se sincronizó con éxito).
     */
    public record SourceResult(
            String source,
            String status,
            boolean updated,
            int fetched,
            int added,
            Instant lastSyncAt,
            Instant nextSyncAt,
            long minutesUntilNextSync,
            String message
    ) {
    }
}
