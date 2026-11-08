package com.skillmap.api.infrastructure.scheduling;

import com.skillmap.api.application.service.JobService;
import com.skillmap.api.infrastructure.jobs.ArbeitnowJobSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Sincronización automática de Arbeitnow (ver SourceAutoSync): cada interval-days días (de 1 a 5; por defecto, 1),
 * al arrancar y luego cada hora. Sin ella Arbeitnow solo se actualizaba con el cron diario (06:00 UTC, con la app de
 * escritorio casi nunca abierta a esa hora) o con "Recalcular", y en una instalación nueva se quedaba con la fecha de
 * las ofertas incluidas. Solo existe en el perfil de escritorio.
 */
@Component
@ConditionalOnProperty(name = "skillmap.sync.arbeitnow.enabled", havingValue = "true", matchIfMissing = true)
public class ArbeitnowAutoSync extends SourceAutoSync {

    static final int MIN_DAYS = 1;
    static final int MAX_DAYS = 5;

    @Autowired
    public ArbeitnowAutoSync(JobService jobService, Clock clock,
                             @Value("${skillmap.sync.arbeitnow.interval-days:1}") int intervalDays,
                             @Value("${jobs.sync.min-interval}") Duration retryInterval) {
        this(jobService, clock, intervalDays, retryInterval, daemonThread("arbeitnow-auto-sync"));
    }

    /** Permite a los tests ejecutar el chequeo inicial en el mismo hilo. */
    ArbeitnowAutoSync(JobService jobService, Clock clock, int intervalDays, Duration retryInterval,
                      Executor backgroundExecutor) {
        super(jobService, clock, ArbeitnowJobSource.NAME, intervalDays, MIN_DAYS, MAX_DAYS, retryInterval,
                backgroundExecutor);
    }
}
