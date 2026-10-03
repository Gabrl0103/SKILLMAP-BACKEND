package com.skillmap.api.infrastructure.scheduling;

import com.skillmap.api.application.service.JobService;
import com.skillmap.api.domain.exception.JobSourceException;
import com.skillmap.api.domain.model.JobSyncReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Adaptador de entrada: lanza la sincronización de ofertas una vez al día. */
@Component
public class JobSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobSyncScheduler.class);

    private final JobService jobService;

    public JobSyncScheduler(JobService jobService) {
        this.jobService = jobService;
    }

    @Scheduled(cron = "${jobs.sync.cron}", zone = "UTC")
    public void syncDaily() {
        try {
            JobSyncReport report = jobService.sync();
            report.sources().forEach(s -> log.info("Sincronización diaria - {}: {} ({})",
                    s.source(), s.status(), s.message()));
        } catch (JobSourceException e) {
            log.warn("La sincronización diaria de ofertas falló: {}", e.getMessage());
        }
    }
}
