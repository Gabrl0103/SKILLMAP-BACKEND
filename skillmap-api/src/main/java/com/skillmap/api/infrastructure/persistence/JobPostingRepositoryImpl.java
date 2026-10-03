package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.repository.JobPostingRepository;
import com.skillmap.api.infrastructure.persistence.mapper.JobPostingPersistenceMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Adaptador del puerto JobPostingRepository sobre Spring Data JPA. */
@Repository
public class JobPostingRepositoryImpl implements JobPostingRepository {

    private final JobPostingJpaRepository jpaRepository;

    public JobPostingRepositoryImpl(JobPostingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Set<String> findExternalIdsBySource(String source) {
        return jpaRepository.findExternalIdsBySource(source);
    }

    @Override
    public void saveAll(Collection<JobPosting> postings) {
        jpaRepository.saveAll(postings.stream().map(JobPostingPersistenceMapper::toEntity).toList());
    }

    @Override
    public List<JobPosting> findAll() {
        return jpaRepository.findAll().stream().map(JobPostingPersistenceMapper::toDomain).toList();
    }

    @Override
    public List<JobPosting> findNewest(int limit) {
        return jpaRepository.findAllByOrderByPublishedAtDescIdDesc(PageRequest.of(0, limit)).stream()
                .map(JobPostingPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public Map<String, Long> countBySource() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jpaRepository.countGroupedBySource().forEach(c -> counts.put(c.getSource(), c.getPostings()));
        return counts;
    }

    @Override
    @Transactional
    public int deletePublishedBefore(Instant cutoff) {
        return jpaRepository.deletePublishedBefore(cutoff);
    }

    @Override
    @Transactional
    public int deleteAllButNewest(int max) {
        List<Long> ids = jpaRepository.findIdsNewestFirst();
        if (ids.size() <= max) {
            return 0;
        }
        List<Long> surplus = ids.subList(max, ids.size());
        jpaRepository.deleteAllByIdInBatch(surplus);
        return surplus.size();
    }
}
