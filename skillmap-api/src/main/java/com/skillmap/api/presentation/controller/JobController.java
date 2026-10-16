package com.skillmap.api.presentation.controller;

import com.skillmap.api.application.service.JobService;
import com.skillmap.api.infrastructure.jobs.RemotiveJobSource;
import com.skillmap.api.infrastructure.scheduling.RemotiveAutoSync;
import com.skillmap.api.presentation.dto.JobPostingResponse;
import com.skillmap.api.presentation.dto.JobStatsResponse;
import com.skillmap.api.presentation.dto.JobSyncResponse;
import com.skillmap.api.presentation.mapper.JobResponseMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Ofertas de empleo reales: sincronización manual, listado y estadísticas. */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;
    private final ObjectProvider<RemotiveAutoSync> remotiveAutoSync;

    public JobController(JobService jobService, ObjectProvider<RemotiveAutoSync> remotiveAutoSync) {
        this.jobService = jobService;
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
}
