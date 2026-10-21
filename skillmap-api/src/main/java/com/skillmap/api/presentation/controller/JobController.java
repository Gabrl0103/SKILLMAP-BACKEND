package com.skillmap.api.presentation.controller;

import com.skillmap.api.application.service.JobRecommendationService;
import com.skillmap.api.application.service.JobService;
import com.skillmap.api.infrastructure.jobs.RemotiveJobSource;
import com.skillmap.api.infrastructure.scheduling.RemotiveAutoSync;
import com.skillmap.api.presentation.dto.JobDetailResponse;
import com.skillmap.api.presentation.dto.JobPostingResponse;
import com.skillmap.api.presentation.dto.JobRecommendationResponse;
import com.skillmap.api.presentation.dto.JobStatsResponse;
import com.skillmap.api.presentation.dto.JobSyncResponse;
import com.skillmap.api.presentation.filter.AuthenticationFilter;
import com.skillmap.api.presentation.mapper.JobResponseMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Ofertas de empleo reales: sincronización manual, listado, estadísticas, recomendadas y detalle. */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;
    private final JobRecommendationService recommendationService;
    private final ObjectProvider<RemotiveAutoSync> remotiveAutoSync;

    public JobController(JobService jobService, JobRecommendationService recommendationService,
                         ObjectProvider<RemotiveAutoSync> remotiveAutoSync) {
        this.jobService = jobService;
        this.recommendationService = recommendationService;
        this.remotiveAutoSync = remotiveAutoSync;
    }

    // TODO versión web: key y sync solo admin.
    @PostMapping("/sync")
    public JobSyncResponse sync() {
        return JobResponseMapper.toResponse(jobService.sync());
    }

    @GetMapping
    public List<JobPostingResponse> getNewestJobs(@RequestParam(defaultValue = "50") int limit) {
        return jobService.getNewestJobs(limit).stream()
                .map(JobResponseMapper::toResponse)
                .toList();
    }

    @GetMapping("/stats")
    public JobStatsResponse getStats() {
        RemotiveAutoSync autoSync = remotiveAutoSync.getIfAvailable();
        Map<String, Integer> autoSyncDays = autoSync == null
                ? Map.of()
                : Map.of(RemotiveJobSource.NAME, autoSync.intervalDays());
        return JobResponseMapper.toResponse(jobService.getStats(), autoSyncDays);
    }

    /**
     * Ofertas guardadas que mejor encajan con el objetivo y el progreso del usuario, sin IA. limit: de 1 a 30 (10 si no
     * viene). goalId: opcional; sin él, el objetivo guardado del usuario (o el primero). 404 si no existe.
     */
    @GetMapping("/recommended")
    public List<JobRecommendationResponse> getRecommended(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                                          @RequestParam(required = false) Long goalId,
                                                          @RequestParam(defaultValue = "" + JobRecommendationService.DEFAULT_LIMIT) int limit) {
        return recommendationService.recommend(userId, goalId, limit).stream()
                .map(JobResponseMapper::toResponse)
                .toList();
    }

    /** Una oferta con su descripción completa (para el Analizador). 404 si no existe. */
    @GetMapping("/{id}")
    public JobDetailResponse getJob(@PathVariable Long id) {
        return JobResponseMapper.toDetailResponse(jobService.getJob(id));
    }
}
