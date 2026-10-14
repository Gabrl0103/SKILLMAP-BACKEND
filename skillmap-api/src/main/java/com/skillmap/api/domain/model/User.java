package com.skillmap.api.domain.model;

import java.time.Instant;

/**
 * Cuenta de usuario. El correo se guarda siempre en minúsculas (ver UserFactory) y la
 * contraseña solo como hash. Sin toString: ni el hash debe acabar en un log por descuido.
 */
public class User {

    private final Long id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final Instant createdAt;

    public User(Long id, String name, String email, String passwordHash, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
}
