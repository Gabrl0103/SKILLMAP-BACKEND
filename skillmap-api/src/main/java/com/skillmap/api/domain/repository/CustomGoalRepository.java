package com.skillmap.api.domain.repository;

import com.skillmap.api.domain.model.CustomGoal;

import java.util.List;
import java.util.Optional;

/** Puerto de salida para los objetivos propios. Toda consulta va filtrada por usuario. */
public interface CustomGoalRepository {

    /** Los del usuario, del más antiguo al más nuevo. */
    List<CustomGoal> findByUserId(Long userId);

    /** El objetivo propio con ese id, solo si es del usuario. */
    Optional<CustomGoal> find(Long userId, Long id);

    /** Crea (id null) o reemplaza. */
    CustomGoal save(CustomGoal goal);

    void delete(Long userId, Long id);

    /** Todos los del usuario (al eliminar su cuenta). */
    void deleteByUserId(Long userId);
}
