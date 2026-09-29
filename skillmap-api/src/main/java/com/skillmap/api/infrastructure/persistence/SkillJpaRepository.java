package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.infrastructure.persistence.entity.SkillEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio técnico de Spring Data JPA. Solo lo usa SkillRepositoryImpl;
 * el resto de la aplicación no debería importar esta interfaz directamente.
 */
public interface SkillJpaRepository extends JpaRepository<SkillEntity, Long> {
}
