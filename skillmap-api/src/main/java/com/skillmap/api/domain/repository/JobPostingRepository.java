package com.skillmap.api.domain.repository;

import com.skillmap.api.domain.model.JobPosting;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Puerto de salida para persistir las ofertas de empleo. */
public interface JobPostingRepository {
    Set<String> findExternalIdsBySource(String source);
    void saveAll(Collection<JobPosting> postings);
    List<JobPosting> findAll();
    /** Las más recientes primero, según la fecha de publicación. */
    List<JobPosting> findNewest(int limit);
    long count();
    Map<String, Long> countBySource();
    /** @return cuántas se borraron */
    int deletePublishedBefore(Instant cutoff);
    /** Deja solo las {@code max} publicadas más recientemente. @return cuántas se borraron */
    int deleteAllButNewest(int max);
}
