package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.infrastructure.persistence.entity.JobSourceSyncEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio técnico de Spring Data JPA. Solo lo usa JobSyncStateRepositoryImpl. */
public interface JobSourceSyncJpaRepository extends JpaRepository<JobSourceSyncEntity, String> {
}
