package com.skillmap.api.application.service;

import com.skillmap.api.domain.model.CustomGoal;

import java.util.List;

/**
 * Objetivos propios ("Otro objetivo"): de 3 a 8 habilidades del catálogo, como mucho 3 por usuario (reglas en
 * CustomGoalFactory). Elegir uno es PUT /api/auth/me/goal, como con los base.
 */
public interface CustomGoalService {

    List<CustomGoal> list(Long userId);

    CustomGoal create(Long userId, String name, String description, List<Long> skillIds);

    /** @throws com.skillmap.api.domain.exception.GoalNotEditableException si id es de un objetivo base */
    CustomGoal update(Long userId, Long id, String name, String description, List<Long> skillIds);

    /**
     * Borra el objetivo y su historial de preparación. Si era el elegido, el usuario vuelve a su último objetivo base
     * (o al primero). El progreso por habilidad no se toca.
     * @return el objetivo elegido después de borrar (null si no queda ninguno)
     */
    Long delete(Long userId, Long id);
}
