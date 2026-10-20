package com.skillmap.api.application.service;

import com.skillmap.api.domain.model.ReadinessSnapshot;

import java.util.List;

/** Historial del % de preparación por usuario y objetivo: un registro por día, solo cuando el % cambia. */
public interface ReadinessHistoryService {

    int DEFAULT_DAYS = 30;
    int MAX_DAYS = 365;

    /** Tras cambiar el estado de una habilidad: registra los objetivos que la incluyen. */
    void recordSkillChange(Long userId, Long skillId);

    /** Registra el % actual del usuario en ese objetivo (al elegirlo). */
    void recordGoal(Long userId, Long goalId);

    /**
     * Los últimos {@code days} días (hoy incluido), ordenados por día. Sin ningún registro en ese objetivo
     * guarda antes el % actual como punto inicial.
     */
    List<ReadinessSnapshot> getHistory(Long userId, Long goalId, int days);
}
