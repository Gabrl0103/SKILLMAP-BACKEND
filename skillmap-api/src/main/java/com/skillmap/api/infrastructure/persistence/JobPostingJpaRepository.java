package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.infrastructure.persistence.entity.JobPostingEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Repositorio técnico de Spring Data JPA. Solo lo usa JobPostingRepositoryImpl. */
public interface JobPostingJpaRepository extends JpaRepository<JobPostingEntity, Long> {

    @Query("select j.externalId from JobPostingEntity j where j.source = :source")
    Set<String> findExternalIdsBySource(@Param("source") String source);

    List<JobPostingEntity> findAllByOrderByPublishedAtDescIdDesc(Pageable pageable);

    @Query("select j.id from JobPostingEntity j order by j.publishedAt desc, j.id desc")
    List<Long> findIdsNewestFirst();

    @Query("select j.source as source, count(j) as postings from JobPostingEntity j group by j.source")
    List<SourceCount> countGroupedBySource();

    @Modifying
    @Query("delete from JobPostingEntity j where j.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);

    interface SourceCount {
        String getSource();
        long getPostings();
    }
}
