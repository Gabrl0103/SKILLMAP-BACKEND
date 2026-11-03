package com.skillmap.api.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migración de la columna user_skills.updated_at (cuándo cambió cada habilidad de estado). ddl-auto=update la agrega
 * vacía a una base existente; al arrancar, las filas sin fecha reciben la fecha de alta de su cuenta. Es la más
 * antigua posible y no una inventada: así lo marcado antes de esta versión no cuenta como avance de esta semana ni de
 * este mes (salvo en una cuenta creada esta semana, donde la fecha es casi exacta). Idempotente: con todas las filas
 * fechadas no toca nada.
 */
@Component
@Order(0)
public class UserSkillDatesBackfill implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSkillDatesBackfill.class);

    private final UserSkillJpaRepository jpaRepository;

    public UserSkillDatesBackfill(UserSkillJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int filled = jpaRepository.fillMissingUpdatedAt();
        if (filled > 0) log.info("Progreso sin fecha de cambio: {} filas reciben la fecha de alta de su cuenta", filled);
    }
}
