package com.skillmap.api.infrastructure.persistence.entity;

import com.skillmap.api.domain.model.SkillStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "user_skills", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_skills_user_skill", columnNames = {"user_id", "skill_id"}))
public class UserSkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SkillStatus status;

    /**
     * Cuándo pasó a este estado. Admite null en la base para que ddl-auto=update pueda agregar la columna a una base
     * con filas: esas filas reciben su valor al arrancar (UserSkillDatesBackfill) y las nuevas siempre lo traen.
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected UserSkillEntity() {
        // Requerido por JPA.
    }

    public UserSkillEntity(Long id, Long userId, Long skillId, SkillStatus status, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.skillId = skillId;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getSkillId() { return skillId; }
    public SkillStatus getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
}
