package com.skillmap.api.infrastructure.scheduling;

import com.skillmap.api.application.service.JobService;
import com.skillmap.api.infrastructure.jobs.RemotiveJobSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Sincronización automática de Remotive (ver SourceAutoSync): cada interval-days días (de 3 a 5, por su límite de
 * uso), al arrancar y luego cada hora. Solo existe en el perfil de escritorio.
 */
@Component
@ConditionalOnProperty(name = "skillmap.sync.remotive.enabled", havingValue = "true", matchIfMissing = true)
public class RemotiveAutoSync extends SourceAutoSync {

    static final int MIN_DAYS = 3;
    static final int MAX_DAYS = 5;

    @Autowired
    public RemotiveAutoSync(JobService jobService, Clock clock,
                            @Value("${skillmap.sync.remotive.interval-days:4}") int intervalDays,
                            @Value("${jobs.sync.min-interval}") Duration retryInterval) {
        this(jobService, clock, intervalDays, retryInterval, daemonThread("remotive-auto-sync"));
    }

    /** Permite a los tests ejecutar el chequeo inicial en el mismo hilo. */
    RemotiveAutoSync(JobService jobService, Clock clock, int intervalDays, Duration retryInterval,
                     Executor backgroundExecutor) {
        super(jobService, clock, RemotiveJobSource.NAME, intervalDays, MIN_DAYS, MAX_DAYS, retryInterval,
                backgroundExecutor);
    }
}
