package com.skillmap.api.domain.model;

/** Qué pasó con una fuente en una sincronización. */
public enum SourceSyncStatus {
    SYNCED,
    /** No se llamó a la fuente porque se sincronizó hace poco (protege su límite de uso). */
    SKIPPED,
    FAILED
}
