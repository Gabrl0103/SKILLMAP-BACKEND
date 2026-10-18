package com.skillmap.api.domain.repository;

import com.skillmap.api.domain.model.Session;

import java.time.Instant;
import java.util.Optional;

/** Puerto de salida para las sesiones. Siempre se trabaja con el hash del token, nunca con el token. */
public interface SessionRepository {
    Session save(Session session);
    Optional<Session> findByTokenHash(String tokenHash);
    void deleteByTokenHash(String tokenHash);
    void deleteExpired(Instant now);

    /** Cierra todas las sesiones del usuario salvo la de ese hash (la que hace la petición). */
    void deleteByUserIdExcept(Long userId, String keepTokenHash);

    void deleteByUserId(Long userId);
}
