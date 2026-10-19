package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.infrastructure.persistence.entity.ProfileLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfileLinkJpaRepository extends JpaRepository<ProfileLinkEntity, Long> {
    List<ProfileLinkEntity> findByUserIdOrderByIdAsc(Long userId);
    Optional<ProfileLinkEntity> findByIdAndUserId(Long id, Long userId);
    long countByUserId(Long userId);
    long deleteByIdAndUserId(Long id, Long userId);
    void deleteByUserId(Long userId);
}
