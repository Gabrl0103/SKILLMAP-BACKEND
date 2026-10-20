package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.infrastructure.persistence.entity.ReadinessSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReadinessSnapshotJpaRepository extends JpaRepository<ReadinessSnapshotEntity, Long> {
    Optional<ReadinessSnapshotEntity> findFirstByUserIdAndGoalIdOrderByCapturedOnDesc(Long userId, Long goalId);
    List<ReadinessSnapshotEntity> findByUserIdAndGoalIdAndCapturedOnGreaterThanEqualOrderByCapturedOnAsc(
            Long userId, Long goalId, LocalDate from);
    void deleteByUserId(Long userId);
}
