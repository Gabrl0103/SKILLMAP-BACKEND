package com.skillmap.api.domain.model;

import java.time.Instant;

/**
 * Cuenta de usuario. El correo se guarda siempre en minúsculas (ver UserFactory) y la
 * contraseña solo como hash. El titular (headline) es opcional: null si no hay. goalId es el
 * objetivo profesional elegido (base o propio): null si todavía no eligió ninguno. baseGoalId es el último objetivo
 * base que eligió (null si nunca eligió uno): a él vuelve si borra el objetivo propio que tenía elegido.
 * Sin toString: ni el hash debe acabar en un log por descuido.
 */
public class User {

    private final Long id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final Instant createdAt;
    private final String headline;
    private final Long goalId;
    private final Long baseGoalId;

    public User(Long id, String name, String email, String passwordHash, Instant createdAt, String headline,
                Long goalId) {
        this(id, name, email, passwordHash, createdAt, headline, goalId, null);
    }

    public User(Long id, String name, String email, String passwordHash, Instant createdAt, String headline,
                Long goalId, Long baseGoalId) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.headline = headline;
        this.goalId = goalId;
        this.baseGoalId = baseGoalId;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
    public String getHeadline() { return headline; }
    public Long getGoalId() { return goalId; }
    public Long getBaseGoalId() { return baseGoalId; }

    /** Copia con otro nombre y titular (ya validados por UserFactory). */
    public User withProfile(String newName, String newHeadline) {
        return new User(id, newName, email, passwordHash, createdAt, newHeadline, goalId, baseGoalId);
    }

    public User withPasswordHash(String newPasswordHash) {
        return new User(id, name, email, newPasswordHash, createdAt, headline, goalId, baseGoalId);
    }

    /**
     * Copia con otro objetivo (ya comprobado que el usuario puede verlo). El progreso por habilidad no depende de él.
     * Si es base, pasa a ser también el "base anterior".
     */
    public User withGoal(CareerGoal goal) {
        return new User(id, name, email, passwordHash, createdAt, headline, goal.getId(),
                goal.isCustom() ? baseGoalId : goal.getId());
    }

    /** Copia con otro objetivo por id, sin tocar el "base anterior". */
    public User withGoal(Long newGoalId) {
        return new User(id, name, email, passwordHash, createdAt, headline, newGoalId, baseGoalId);
    }
}
