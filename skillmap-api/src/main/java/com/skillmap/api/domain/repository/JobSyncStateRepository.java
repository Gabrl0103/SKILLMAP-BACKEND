package com.skillmap.api.domain.repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Puerto de salida: recuerda cuándo se sincronizó con éxito cada fuente. */
public interface JobSyncStateRepository {
    Optional<Instant> findLastSync(String source);
    Map<String, Instant> findAll();
    void saveLastSync(String source, Instant syncedAt);
}
