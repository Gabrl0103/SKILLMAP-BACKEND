package com.skillmap.api.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migración de la columna users.base_goal_id (último objetivo base elegido, al que se vuelve al borrar el objetivo
 * propio elegido). ddl-auto=update la agrega vacía a una base existente; al arrancar, las cuentas que ya tenían un
 * objetivo elegido (siempre base antes de esta versión) lo reciben. Idempotente: sin filas pendientes no toca nada.
 * Las tablas custom_goals y custom_goal_skills no necesitan nada: ddl-auto=update las crea vacías.
 */
@Component
@Order(0)
public class UserBaseGoalBackfill implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UserBaseGoalBackfill.class);

    private final UserJpaRepository jpaRepository;

    public UserBaseGoalBackfill(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int filled = jpaRepository.fillMissingBaseGoal();
        if (filled > 0) log.info("Objetivo base anterior: {} cuentas reciben su objetivo elegido", filled);
    }
}
