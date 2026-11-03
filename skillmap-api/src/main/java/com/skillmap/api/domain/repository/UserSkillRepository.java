package com.skillmap.api.domain.repository;

import com.skillmap.api.domain.model.SkillStatus;
import com.skillmap.api.domain.model.UserSkill;

import java.util.Map;
import java.util.Optional;

/** Puerto de salida para el progreso de cada usuario en el catálogo de habilidades. */
public interface UserSkillRepository {

    /** Estado por id de habilidad; las que no aparecen están pendientes. */
    Map<Long, SkillStatus> findStatusesByUserId(Long userId);

    /** Progreso (estado y fecha del cambio) por id de habilidad; las que no aparecen están pendientes. */
    Map<Long, UserSkill> findByUserId(Long userId);

    /** Progreso del usuario en esa habilidad; vacío si está pendiente. */
    Optional<UserSkill> find(Long userId, Long skillId);

    /** Crea o actualiza el progreso del usuario en esa habilidad. */
    void save(UserSkill userSkill);

    /** Borra el progreso del usuario en esa habilidad (vuelve a pendiente). Sin progreso guardado no hace nada. */
    void delete(Long userId, Long skillId);

    /** Borra todo el progreso del usuario (al eliminar su cuenta). */
    void deleteByUserId(Long userId);
}
