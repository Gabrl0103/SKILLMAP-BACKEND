package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);

    /** Sin distinguir mayúsculas: también encuentra una cuenta antigua guardada con mayúsculas. */
    Optional<UserEntity> findFirstByEmailIgnoreCaseOrderByIdAsc(String email);
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Cuentas de antes de base_goal_id con un objetivo elegido: ese objetivo es base (los propios no existían y sus ids
     * empiezan en 1.000.000), así que pasa a ser también su "base anterior". Devuelve cuántas.
     */
    @Modifying
    @Query(value = "update users set base_goal_id = goal_id "
            + "where base_goal_id is null and goal_id is not null and goal_id < 1000000", nativeQuery = true)
    int fillMissingBaseGoal();
}
