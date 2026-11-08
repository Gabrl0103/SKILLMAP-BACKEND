package com.skillmap.api.infrastructure.scheduling;

import com.skillmap.api.application.service.JobService;
import com.skillmap.api.domain.model.JobStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Adaptador de entrada: la app de escritorio rara vez está abierta a la hora del cron diario, así que al arrancar (en
 * segundo plano) y luego cada hora se mira cuándo se actualizó una fuente por última vez y, si pasaron al menos
 * intervalDays días, se sincroniza solo esa fuente. Tras un fallo no se vuelve a intentar hasta pasado
 * jobs.sync.min-interval (y JobService nunca consulta una fuente dos veces dentro de ese intervalo).
 * Una subclase por fuente (RemotiveAutoSync, ArbeitnowAutoSync); solo existen en el perfil de escritorio.
 */
public abstract class SourceAutoSync {

    private static final Logger log = LoggerFactory.getLogger(SourceAutoSync.class);

    private final JobService jobService;
    private final Clock clock;
    private final String source;
    private final int intervalDays;
    private final Duration retryInterval;
    private final Executor backgroundExecutor;
    /** Último intento fallido (en memoria): limita los reintentos a uno cada retryInterval. */
    private Instant lastFailureAt;

    protected SourceAutoSync(JobService jobService, Clock clock, String source, int intervalDays, int minDays,
                             int maxDays, Duration retryInterval, Executor backgroundExecutor) {
        this.jobService = jobService;
        this.clock = clock;
        this.source = source;
        this.intervalDays = Math.max(minDays, Math.min(maxDays, intervalDays));
        if (this.intervalDays != intervalDays) {
            log.warn("Intervalo de sincronización automática de {} = {} días, fuera de {}-{}: se usa {}",
                    source, intervalDays, minDays, maxDays, this.intervalDays);
        }
        this.retryInterval = retryInterval;
        this.backgroundExecutor = backgroundExecutor;
    }

    public String source() {
        return source;
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
            jobService.syncSource(source).sources().forEach(s ->
                    log.info("Sincronización automática - {}: {} ({})", s.source(), s.status(), s.message()));
            lastFailureAt = null;
        } catch (RuntimeException e) {
            lastFailureAt = now;
            log.warn("Sincronización automática de {} falló: {}. Se reintenta en {} h como pronto.",
                    source, e.getMessage(), retryInterval.toHours());
        }
    }

    private Instant lastSync() {
        return jobService.getStats().sources().stream()
                .filter(s -> s.name().equals(source))
                .findFirst()
                .map(JobStats.SourceStats::lastSyncAt)
                .orElse(null);
    }

    protected static Executor daemonThread(String name) {
        return task -> {
            Thread thread = new Thread(task, name);
            // Cerrar la app no espera a que termine.
            thread.setDaemon(true);
            thread.start();
        };
    }
}
