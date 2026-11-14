package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.infrastructure.persistence.entity.CustomGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomGoalJpaRepository extends JpaRepository<CustomGoalEntity, Long> {
    List<CustomGoalEntity> findByUserIdOrderByIdAsc(Long userId);
    Optional<CustomGoalEntity> findByIdAndUserId(Long id, Long userId);
    void deleteByUserId(Long userId);
}
