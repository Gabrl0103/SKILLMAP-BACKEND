package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.infrastructure.persistence.entity.SkillEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

/**
 * Repositorio técnico de Spring Data JPA. Solo lo usa SkillRepositoryImpl;
 * el resto de la aplicación no debería importar esta interfaz directamente.
 */
public interface SkillJpaRepository extends JpaRepository<SkillEntity, Long> {

    /** El catálogo global, en el orden en que se sembró. */
    List<SkillEntity> findByOwnerIdIsNullOrderByIdAsc();

    List<SkillEntity> findByOwnerIdOrderByIdAsc(Long ownerId);

    @Modifying(flushAutomatically = true)
    @Query("delete from SkillEntity s where s.ownerId = :ownerId and s.id in :ids")
    int deleteOwned(Long ownerId, Collection<Long> ids);

    @Modifying(flushAutomatically = true)
    @Query("delete from SkillEntity s where s.ownerId = :ownerId")
    int deleteByOwner(Long ownerId);
}
