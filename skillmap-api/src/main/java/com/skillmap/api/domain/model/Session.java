package com.skillmap.api.domain.model;

import java.time.Instant;

/**
 * Sesión abierta. Del token solo se guarda su hash: quien lea la base no puede usarlo.
 */
public record Session(String tokenHash, Long userId, Instant expiresAt) {

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    @Override
    public String toString() {
        return "Session[userId=" + userId + ", expiresAt=" + expiresAt + "]";
    }
}
