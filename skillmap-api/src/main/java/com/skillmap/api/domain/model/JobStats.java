package com.skillmap.api.domain.model;

import java.time.Instant;
import java.util.List;

/** Estado del almacén de ofertas: cuántas hay, de dónde vienen y cuándo se sincronizó por última vez. */
public record JobStats(
        long totalPostings,
        List<SourceStats> sources,
        Instant lastSyncAt
) {

    /**
     * @param nextSyncAt   desde cuándo se puede volver a sincronizar (null si nunca se sincronizó)
     * @param lastFailedAt último intento fallido desde la última sincronización correcta, en esta ejecución de la app
     *                     (null si no lo hubo): así la app no muestra una fecha vieja sin explicar por qué
     */
    public record SourceStats(String name, long postings, Instant lastSyncAt, Instant nextSyncAt,
                              Instant lastFailedAt) {

        public SourceStats(String name, long postings, Instant lastSyncAt, Instant nextSyncAt) {
            this(name, postings, lastSyncAt, nextSyncAt, null);
        }
    }
}
