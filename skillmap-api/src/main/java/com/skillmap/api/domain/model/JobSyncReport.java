package com.skillmap.api.domain.model;

import java.util.List;

/**
 * Resumen de una sincronización completa.
 *
 * @param demandRecalculated false si había menos ofertas de las necesarias y se dejó la demanda anterior
 */
public record JobSyncReport(
        List<SourceSyncResult> sources,
        int totalPostings,
        boolean demandRecalculated
) {
}
