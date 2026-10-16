package com.skillmap.api.presentation.dto;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/jobs/stats: total de ofertas, desglose por fuente y última sincronización.
 * Las fechas son instantes UTC en ISO-8601 (ej. 2026-10-02T12:00:00Z); null si nunca se sincronizó.
 */
public record JobStatsResponse(
        long totalJobs,
        List<SourceStats> sources,
        Instant lastSyncAt
) {

    /**
     * nextSyncAt: desde cuándo se puede volver a sincronizar la fuente.
     * autoSyncDays: cada cuántos días se sincroniza sola (null si no hay sincronización automática).
     */
    public record SourceStats(String name, long jobs, Instant lastSyncAt, Instant nextSyncAt, Integer autoSyncDays) {
    }
}
