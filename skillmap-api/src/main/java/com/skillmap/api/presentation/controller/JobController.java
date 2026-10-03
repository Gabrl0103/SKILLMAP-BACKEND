package com.skillmap.api.presentation.controller;

import com.skillmap.api.application.service.JobService;
import com.skillmap.api.presentation.dto.JobPostingResponse;
import com.skillmap.api.presentation.dto.JobStatsResponse;
import com.skillmap.api.presentation.dto.JobSyncResponse;
import com.skillmap.api.presentation.mapper.JobResponseMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Ofertas de empleo reales: sincronización manual, listado y estadísticas. */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

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
        return JobResponseMapper.toResponse(jobService.getStats());
    }
}
