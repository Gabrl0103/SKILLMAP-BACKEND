package com.skillmap.api.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/** Activa la sincronización diaria de ofertas y expone un reloj que los tests pueden fijar. */
@Configuration
@EnableScheduling
public class JobSyncConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
