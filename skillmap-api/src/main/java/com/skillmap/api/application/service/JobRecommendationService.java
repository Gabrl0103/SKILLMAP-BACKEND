package com.skillmap.api.application.service;

import com.skillmap.api.domain.exception.CareerGoalNotFoundException;
import com.skillmap.api.domain.model.JobRecommendation;

import java.util.List;

/** Puerto de entrada: ofertas guardadas que mejor encajan con el objetivo y el progreso del usuario. */
public interface JobRecommendationService {

    int DEFAULT_LIMIT = 10;
    int MAX_LIMIT = 30;

    /**
     * Se calcula en cada llamada con el progreso actual. Sin goalId usa el objetivo guardado del usuario o, si no
     * tiene, el primero (como el resto de la app); con goalId, ese (el que muestra Mi Ruta, que en cuentas antiguas
     * puede venir del navegador). Sin objetivos u ofertas, lista vacía. limit de 1 a MAX_LIMIT.
     *
     * @throws CareerGoalNotFoundException si goalId no existe
     */
    List<JobRecommendation> recommend(Long userId, Long goalId, int limit);
}
