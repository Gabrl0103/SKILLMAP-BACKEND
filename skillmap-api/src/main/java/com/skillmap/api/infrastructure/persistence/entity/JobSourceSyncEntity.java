package com.skillmap.api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Última sincronización correcta de cada fuente de ofertas. */
@Entity
@Table(name = "job_source_syncs")
public class JobSourceSyncEntity {

    @Id
    @Column(length = 50)
    private String source;

    private Instant lastSyncAt;

    protected JobSourceSyncEntity() {
        // Requerido por JPA.
    }

    public JobSourceSyncEntity(String source, Instant lastSyncAt) {
        this.source = source;
        this.lastSyncAt = lastSyncAt;
    }

    public String getSource() { return source; }
    public Instant getLastSyncAt() { return lastSyncAt; }
}
