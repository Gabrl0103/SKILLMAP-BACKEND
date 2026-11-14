package com.skillmap.api.infrastructure.persistence.entity;

import com.skillmap.api.domain.model.DemandSource;
import com.skillmap.api.domain.model.SkillStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Modelo de persistencia (JPA). A propósito es una clase distinta de Skill
 * (el modelo de dominio): así, si mañana cambia la base de datos, el
 * dominio ni se entera.
 */
@Entity
@Table(name = "skills")
public class SkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String category;
    private int demandPercentage;

    @Enumerated(EnumType.STRING)
    private SkillStatus status;

    @Enumerated(EnumType.STRING)
    private DemandSource demandSource;

    // Habilidades propias de un usuario (ver Skill.custom). Nullables: ddl-auto=update las agrega a las bases
    // existentes y las filas de antes quedan como catálogo (sin dueño). Se borran con su objetivo y con la cuenta
    // (CustomGoalServiceImpl y AuthServiceImpl), por eso sin FK a users.
    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "custom_goal_id")
    private Long customGoalId;

    protected SkillEntity() {
        // Requerido por JPA.
    }

    public SkillEntity(Long id, String name, String category, int demandPercentage, SkillStatus status,
                       DemandSource demandSource) {
        this(id, name, category, demandPercentage, status, demandSource, null, null);
    }

    public SkillEntity(Long id, String name, String category, int demandPercentage, SkillStatus status,
                       DemandSource demandSource, Long ownerId, Long customGoalId) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.demandPercentage = demandPercentage;
        this.status = status;
        this.demandSource = demandSource;
        this.ownerId = ownerId;
        this.customGoalId = customGoalId;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public int getDemandPercentage() { return demandPercentage; }
    public SkillStatus getStatus() { return status; }
    public DemandSource getDemandSource() { return demandSource; }
    public Long getOwnerId() { return ownerId; }
    public Long getCustomGoalId() { return customGoalId; }
}
