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

    protected UserSkillEntity() {
        // Requerido por JPA.
    }

    public UserSkillEntity(Long id, Long userId, Long skillId, SkillStatus status) {
        this.id = id;
        this.userId = userId;
        this.skillId = skillId;
        this.status = status;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getSkillId() { return skillId; }
    public SkillStatus getStatus() { return status; }
}
