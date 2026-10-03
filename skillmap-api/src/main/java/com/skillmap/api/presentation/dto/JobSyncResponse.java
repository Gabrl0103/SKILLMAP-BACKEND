package com.skillmap.api.presentation.dto;

import java.time.Instant;
import java.util.List;

/** Resumen de POST /api/jobs/sync: qué pasó con cada fuente y si se recalculó la demanda. */
public record JobSyncResponse(
        List<SourceResult> sources,
        int totalJobs,
        boolean demandRecalculated
) {

    /** status: SYNCED, SKIPPED (sincronizada hace poco) o FAILED. */
    public record SourceResult(
            String source,
            String status,
            int fetched,
            int added,
            Instant lastSyncAt,
            String message
    ) {
    }
}
