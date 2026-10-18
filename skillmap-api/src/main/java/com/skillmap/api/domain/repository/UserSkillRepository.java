package com.skillmap.api.domain.repository;

import com.skillmap.api.domain.model.SkillStatus;
import com.skillmap.api.domain.model.UserSkill;

import java.util.Map;

/** Puerto de salida para el progreso de cada usuario en el catálogo de habilidades. */
public interface UserSkillRepository {

    /** Estado por id de habilidad; las que no aparecen están pendientes. */
    Map<Long, SkillStatus> findStatusesByUserId(Long userId);

    /** Crea o actualiza el progreso del usuario en esa habilidad. */
    void save(UserSkill userSkill);

    /** Borra el progreso del usuario en esa habilidad (vuelve a pendiente). Sin progreso guardado no hace nada. */
    void delete(Long userId, Long skillId);

    /** Borra todo el progreso del usuario (al eliminar su cuenta). */
    void deleteByUserId(Long userId);
}
