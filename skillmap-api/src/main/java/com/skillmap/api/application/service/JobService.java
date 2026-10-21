package com.skillmap.api.application.service;

import com.skillmap.api.domain.exception.JobPostingNotFoundException;
import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.model.JobStats;
import com.skillmap.api.domain.model.JobSyncReport;

import java.util.List;

/** Puerto de entrada: casos de uso de las ofertas de empleo reales. */
public interface JobService {
    /** Trae ofertas nuevas de cada fuente, aplica la retención y recalcula la demanda de las habilidades. */
    JobSyncReport sync();
    /** Como {@link #sync()}, pero solo con la fuente indicada (mismo intervalo mínimo, retención y demanda). */
    JobSyncReport syncSource(String source);
    List<JobPosting> getNewestJobs(int limit);
    /** Una oferta guardada, con su descripción completa. @throws JobPostingNotFoundException si no existe */
    JobPosting getJob(Long id);
    JobStats getStats();
    /** Recalcula la demanda de las habilidades con las ofertas guardadas. @return false si no hay muestra suficiente */
    boolean recalculateDemand();
}
