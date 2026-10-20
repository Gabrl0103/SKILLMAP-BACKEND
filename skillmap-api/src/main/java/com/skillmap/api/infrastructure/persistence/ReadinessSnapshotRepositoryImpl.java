package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.domain.model.ReadinessSnapshot;
import com.skillmap.api.domain.repository.ReadinessSnapshotRepository;
import com.skillmap.api.infrastructure.persistence.entity.ReadinessSnapshotEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class ReadinessSnapshotRepositoryImpl implements ReadinessSnapshotRepository {

    private final ReadinessSnapshotJpaRepository jpaRepository;

    public ReadinessSnapshotRepositoryImpl(ReadinessSnapshotJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<ReadinessSnapshot> findLatest(Long userId, Long goalId) {
        return jpaRepository.findFirstByUserIdAndGoalIdOrderByCapturedOnDesc(userId, goalId)
                .map(ReadinessSnapshotRepositoryImpl::toDomain);
    }

    @Override
    public List<ReadinessSnapshot> findSince(Long userId, Long goalId, LocalDate from) {
        return jpaRepository.findByUserIdAndGoalIdAndCapturedOnGreaterThanEqualOrderByCapturedOnAsc(userId, goalId, from)
                .stream().map(ReadinessSnapshotRepositoryImpl::toDomain).toList();
    }

    @Override
    public ReadinessSnapshot save(ReadinessSnapshot snapshot) {
        return toDomain(jpaRepository.save(new ReadinessSnapshotEntity(snapshot.id(), snapshot.userId(),
                snapshot.goalId(), snapshot.percentage(), snapshot.capturedOn(), snapshot.capturedAt())));
    }

    @Override
    @Transactional
    public void deleteByUserId(Long userId) {
        jpaRepository.deleteByUserId(userId);
    }

    private static ReadinessSnapshot toDomain(ReadinessSnapshotEntity entity) {
        return new ReadinessSnapshot(entity.getId(), entity.getUserId(), entity.getGoalId(), entity.getPercentage(),
                entity.getCapturedOn(), entity.getCapturedAt());
    }
}
