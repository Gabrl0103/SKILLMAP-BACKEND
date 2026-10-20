package com.skillmap.api.domain.repository;

import com.skillmap.api.domain.model.ReadinessSnapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Puerto de salida para el historial de preparación. Toda consulta va filtrada por usuario. */
public interface ReadinessSnapshotRepository {

    /** El del día más reciente del usuario en ese objetivo. */
    Optional<ReadinessSnapshot> findLatest(Long userId, Long goalId);

    /** Desde ese día (incluido), ordenados por día. */
    List<ReadinessSnapshot> findSince(Long userId, Long goalId, LocalDate from);

    /** Crea (id null) o reemplaza el registro. */
    ReadinessSnapshot save(ReadinessSnapshot snapshot);

    /** Todos los del usuario (al eliminar su cuenta). */
    void deleteByUserId(Long userId);
}
