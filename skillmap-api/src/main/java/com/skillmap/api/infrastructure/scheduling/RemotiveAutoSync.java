package com.skillmap.api.infrastructure.scheduling;

import com.skillmap.api.application.service.JobService;
import com.skillmap.api.domain.model.JobStats;
import com.skillmap.api.infrastructure.jobs.RemotiveJobSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Adaptador de entrada: la app de escritorio rara vez está abierta a la hora del cron, así que al
 * arrancar (en segundo plano) y luego cada hora se mira cuándo se actualizó Remotive por última vez
 * y, si pasaron al menos interval-days días, se sincroniza solo esa fuente. Tras un fallo no se
 * vuelve a intentar hasta pasado jobs.sync.min-interval. Solo existe en el perfil de escritorio.
 */
@Component
@ConditionalOnProperty(name = "skillmap.sync.remotive.enabled", havingValue = "true", matchIfMissing = true)
public class RemotiveAutoSync {

    private static final Logger log = LoggerFactory.getLogger(RemotiveAutoSync.class);
    static final int MIN_DAYS = 3;
    static final int MAX_DAYS = 5;

    private final JobService jobService;
    private final Clock clock;
    private final int intervalDays;
    private final Duration retryInterval;
    private final Executor backgroundExecutor;
    /** Último intento fallido (en memoria): limita los reintentos a uno cada retryInterval. */
    private Instant lastFailureAt;

    @Autowired
    public RemotiveAutoSync(JobService jobService, Clock clock,
                            @Value("${skillmap.sync.remotive.interval-days:4}") int intervalDays,
                            @Value("${jobs.sync.min-interval}") Duration retryInterval) {
        this(jobService, clock, intervalDays, retryInterval, RemotiveAutoSync::startDaemonThread);
    }

    /** Permite a los tests ejecutar el chequeo inicial en el mismo hilo. */
    RemotiveAutoSync(JobService jobService, Clock clock, int intervalDays, Duration retryInterval,
                     Executor backgroundExecutor) {
        this.jobService = jobService;
        this.clock = clock;
        this.intervalDays = Math.max(MIN_DAYS, Math.min(MAX_DAYS, intervalDays));
        if (this.intervalDays != intervalDays) {
            log.warn("skillmap.sync.remotive.interval-days={} fuera de {}-{}: se usa {}",
                    intervalDays, MIN_DAYS, MAX_DAYS, this.intervalDays);
        }
        this.retryInterval = retryInterval;
        this.backgroundExecutor = backgroundExecutor;
    }

    public int intervalDays() {
        return intervalDays;
    }

    /** No bloquea el arranque. */
    @EventListener(ApplicationReadyEvent.class)
    public void checkOnStart() {
        backgroundExecutor.execute(this::check);
    }

    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    public synchronized void check() {
        Instant now = clock.instant();
        if (lastFailureAt != null && now.isBefore(lastFailureAt.plus(retryInterval))) {
            return;
        }
        try {
            Instant lastSync = lastSync();
            if (lastSync != null && now.isBefore(lastSync.plus(Duration.ofDays(intervalDays)))) {
                return;
            }
            jobService.syncSource(RemotiveJobSource.NAME).sources().forEach(s ->
                    log.info("Sincronización automática - {}: {} ({})", s.source(), s.status(), s.message()));
            lastFailureAt = null;
        } catch (RuntimeException e) {
            lastFailureAt = now;
            log.warn("Sincronización automática de Remotive falló: {}. Se reintenta en {} h como pronto.",
                    e.getMessage(), retryInterval.toHours());
        }
    }

    private Instant lastSync() {
        return jobService.getStats().sources().stream()
                .filter(s -> s.name().equals(RemotiveJobSource.NAME))
                .findFirst()
                .map(JobStats.SourceStats::lastSyncAt)
                .orElse(null);
    }

    private static void startDaemonThread(Runnable task) {
        Thread thread = new Thread(task, "remotive-auto-sync");
        // Cerrar la app no espera a que termine.
        thread.setDaemon(true);
        thread.start();
    }
}
