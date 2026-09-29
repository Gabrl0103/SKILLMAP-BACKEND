package com.skillmap.api.infrastructure.persistence.entity;

import com.skillmap.api.domain.model.SkillStatus;
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

    protected SkillEntity() {
        // Requerido por JPA.
    }

    public SkillEntity(Long id, String name, String category, int demandPercentage, SkillStatus status) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.demandPercentage = demandPercentage;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public int getDemandPercentage() { return demandPercentage; }
    public SkillStatus getStatus() { return status; }
}
