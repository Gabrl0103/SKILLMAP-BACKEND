package com.skillmap.api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Solo el hash SHA-256 del token (64 caracteres hex), nunca el token. */
@Entity
@Table(name = "sessions")
public class SessionEntity {

    @Id
    @Column(length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Instant expiresAt;

    protected SessionEntity() {
        // Requerido por JPA.
    }

    public SessionEntity(String tokenHash, Long userId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() { return tokenHash; }
    public Long getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
}
