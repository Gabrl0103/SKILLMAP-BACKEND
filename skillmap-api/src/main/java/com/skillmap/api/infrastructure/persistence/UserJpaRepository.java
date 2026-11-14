package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);

    /** Sin distinguir mayúsculas: también encuentra una cuenta antigua guardada con mayúsculas. */
    Optional<UserEntity> findFirstByEmailIgnoreCaseOrderByIdAsc(String email);
    boolean existsByEmailIgnoreCase(String email);
}
