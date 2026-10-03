package com.skillmap.api.application.service;

import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.model.JobStats;
import com.skillmap.api.domain.model.JobSyncReport;

import java.util.List;

/** Puerto de entrada: casos de uso de las ofertas de empleo reales. */
public interface JobService {
    /** Trae ofertas nuevas de cada fuente, aplica la retención y recalcula la demanda de las habilidades. */
    JobSyncReport sync();
    List<JobPosting> getNewestJobs(int limit);
    JobStats getStats();
}
