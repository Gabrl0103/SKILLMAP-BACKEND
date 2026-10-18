package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.domain.model.Session;
import com.skillmap.api.domain.repository.SessionRepository;
import com.skillmap.api.infrastructure.persistence.entity.SessionEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class SessionRepositoryImpl implements SessionRepository {

    private final SessionJpaRepository jpaRepository;

    public SessionRepositoryImpl(SessionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Session save(Session session) {
        jpaRepository.save(new SessionEntity(session.tokenHash(), session.userId(), session.expiresAt()));
        return session;
    }

    @Override
    public Optional<Session> findByTokenHash(String tokenHash) {
        return jpaRepository.findById(tokenHash)
                .map(e -> new Session(e.getTokenHash(), e.getUserId(), e.getExpiresAt()));
    }

    @Override
    public void deleteByTokenHash(String tokenHash) {
        jpaRepository.deleteById(tokenHash);
    }

    @Override
    public void deleteExpired(Instant now) {
        jpaRepository.deleteExpired(now);
    }

    @Override
    public void deleteByUserIdExcept(Long userId, String keepTokenHash) {
        jpaRepository.deleteByUserIdExcept(userId, keepTokenHash);
    }

    @Override
    public void deleteByUserId(Long userId) {
        jpaRepository.deleteByUserId(userId);
    }
}
