package com.skillmap.api.infrastructure.scheduling;

import com.skillmap.api.application.service.JobService;
import com.skillmap.api.domain.exception.JobSourceException;
import com.skillmap.api.domain.model.JobSyncReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * Adaptador de entrada: lanza la sincronización de ofertas una vez al día y, en el primer
 * arranque (sin ofertas guardadas), otra en segundo plano para no esperar al día siguiente.
 * Ambas respetan el intervalo mínimo por fuente que aplica JobService.
 */
@Component
public class JobSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobSyncScheduler.class);

    private final JobService jobService;
    private final boolean syncOnFirstStart;
    private final Executor backgroundExecutor;

    @Autowired
    public JobSyncScheduler(JobService jobService,
                            @Value("${jobs.sync.on-first-start:true}") boolean syncOnFirstStart) {
        this(jobService, syncOnFirstStart, JobSyncScheduler::startDaemonThread);
    }

    /** Permite a los tests ejecutar la sincronización inicial en el mismo hilo. */
    JobSyncScheduler(JobService jobService, boolean syncOnFirstStart, Executor backgroundExecutor) {
        this.jobService = jobService;
        this.syncOnFirstStart = syncOnFirstStart;
        this.backgroundExecutor = backgroundExecutor;
    }

    @Scheduled(cron = "${jobs.sync.cron}", zone = "UTC")
    public void syncDaily() {
        sync("Sincronización diaria");
    }

    /** No bloquea el arranque: las fuentes pueden tardar y la app ya es usable con la demanda sembrada. */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnFirstStart() {
        if (!syncOnFirstStart || jobService.getStats().totalPostings() > 0) {
            return;
        }
        log.info("No hay ofertas guardadas: primera sincronización en segundo plano");
        backgroundExecutor.execute(() -> sync("Primera sincronización"));
    }

    private void sync(String label) {
        try {
            JobSyncReport report = jobService.sync();
            report.sources().forEach(s -> log.info("{} - {}: {} ({})",
                    label, s.source(), s.status(), s.message()));
        } catch (JobSourceException e) {
            log.warn("{} de ofertas falló: {}", label, e.getMessage());
        } catch (RuntimeException e) {
            log.error("{} de ofertas falló", label, e);
        }
    }

    private static void startDaemonThread(Runnable task) {
        Thread thread = new Thread(task, "first-job-sync");
        // Cerrar la app no espera a que termine.
        thread.setDaemon(true);
        thread.start();
    }
}
