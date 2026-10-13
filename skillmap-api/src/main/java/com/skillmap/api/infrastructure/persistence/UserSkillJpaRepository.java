package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.infrastructure.persistence.entity.UserSkillEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSkillJpaRepository extends JpaRepository<UserSkillEntity, Long> {
    List<UserSkillEntity> findByUserId(Long userId);
    Optional<UserSkillEntity> findByUserIdAndSkillId(Long userId, Long skillId);
}
