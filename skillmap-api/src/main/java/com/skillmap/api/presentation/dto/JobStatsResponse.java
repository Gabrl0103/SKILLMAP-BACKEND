package com.skillmap.api.presentation.dto;

import java.time.Instant;
import java.util.List;

/** GET /api/jobs/stats: total de ofertas, desglose por fuente y última sincronización. */
public record JobStatsResponse(
        long totalJobs,
        List<SourceStats> sources,
        Instant lastSyncAt
) {

    public record SourceStats(String name, long jobs, Instant lastSyncAt) {
    }
}
