package com.skillmap.api.domain.model;

import java.time.Instant;

/**
 * Cuenta de usuario. El correo se guarda siempre en minúsculas (ver UserFactory) y la
 * contraseña solo como hash. El titular (headline) es opcional: null si no hay.
 * Sin toString: ni el hash debe acabar en un log por descuido.
 */
public class User {

    private final Long id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final Instant createdAt;
    private final String headline;

    public User(Long id, String name, String email, String passwordHash, Instant createdAt, String headline) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.headline = headline;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
    public String getHeadline() { return headline; }

    /** Copia con otro nombre y titular (ya validados por UserFactory). */
    public User withProfile(String newName, String newHeadline) {
        return new User(id, newName, email, passwordHash, createdAt, newHeadline);
    }

    public User withPasswordHash(String newPasswordHash) {
        return new User(id, name, email, newPasswordHash, createdAt, headline);
    }
}
