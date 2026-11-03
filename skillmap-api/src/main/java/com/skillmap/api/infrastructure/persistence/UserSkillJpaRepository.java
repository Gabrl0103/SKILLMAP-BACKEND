package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.infrastructure.persistence.entity.UserSkillEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserSkillJpaRepository extends JpaRepository<UserSkillEntity, Long> {
    List<UserSkillEntity> findByUserId(Long userId);
    Optional<UserSkillEntity> findByUserIdAndSkillId(Long userId, Long skillId);
    void deleteByUserIdAndSkillId(Long userId, Long skillId);
    void deleteByUserId(Long userId);

    /**
     * Filas sin fecha (de antes de la columna updated_at): la fecha de alta de su cuenta, la más antigua en la que pudo
     * pasar ese cambio. Sin cuenta (no debería ocurrir: se borran con ella), el momento de la migración. Devuelve cuántas.
     */
    @Modifying
    @Query(value = "update user_skills set updated_at = coalesce("
            + "(select u.created_at from users u where u.id = user_skills.user_id), current_timestamp) "
            + "where updated_at is null", nativeQuery = true)
    int fillMissingUpdatedAt();
}
