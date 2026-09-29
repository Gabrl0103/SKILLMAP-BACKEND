package com.skillmap.api.domain.repository;

import com.skillmap.api.domain.model.Skill;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida (Clean/Hexagonal Architecture).
 * El dominio define QUÉ necesita para persistir habilidades, sin saber CÓMO
 * se hace. La implementación real vive en infrastructure/.
 */
public interface SkillRepository {
    List<Skill> findAll();
    Optional<Skill> findById(Long id);
    Skill save(Skill skill);
}
