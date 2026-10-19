package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.infrastructure.persistence.entity.ProfileProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfileProjectJpaRepository extends JpaRepository<ProfileProjectEntity, Long> {
    List<ProfileProjectEntity> findByUserIdOrderByIdAsc(Long userId);
    Optional<ProfileProjectEntity> findByIdAndUserId(Long id, Long userId);
    long countByUserId(Long userId);
    long deleteByIdAndUserId(Long id, Long userId);
    void deleteByUserId(Long userId);
}
