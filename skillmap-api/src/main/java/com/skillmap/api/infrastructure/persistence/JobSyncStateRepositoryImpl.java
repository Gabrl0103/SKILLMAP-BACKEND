package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.domain.repository.JobSyncStateRepository;
import com.skillmap.api.infrastructure.persistence.entity.JobSourceSyncEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Adaptador del puerto JobSyncStateRepository sobre Spring Data JPA. */
@Repository
public class JobSyncStateRepositoryImpl implements JobSyncStateRepository {

    private final JobSourceSyncJpaRepository jpaRepository;

    public JobSyncStateRepositoryImpl(JobSourceSyncJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Instant> findLastSync(String source) {
        return jpaRepository.findById(source).map(JobSourceSyncEntity::getLastSyncAt);
    }

    @Override
    public Map<String, Instant> findAll() {
        Map<String, Instant> syncs = new LinkedHashMap<>();
        jpaRepository.findAll().forEach(s -> syncs.put(s.getSource(), s.getLastSyncAt()));
        return syncs;
    }

    @Override
    public void saveLastSync(String source, Instant syncedAt) {
        jpaRepository.save(new JobSourceSyncEntity(source, syncedAt));
    }
}
