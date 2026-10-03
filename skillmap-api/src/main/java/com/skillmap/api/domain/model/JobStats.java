package com.skillmap.api.domain.model;

import java.time.Instant;
import java.util.List;

/** Estado del almacén de ofertas: cuántas hay, de dónde vienen y cuándo se sincronizó por última vez. */
public record JobStats(
        long totalPostings,
        List<SourceStats> sources,
        Instant lastSyncAt
) {

    public record SourceStats(String name, long postings, Instant lastSyncAt) {
    }
}
