package com.skillmap.api.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Fila de la tabla intermedia goal_skills (objetivo ↔ habilidad, con importancia). */
@Entity
@Table(name = "goal_skills")
public class GoalSkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id")
    private CareerGoalEntity goal;

    private Long skillId;
    private int importance;

    protected GoalSkillEntity() {
    }

    public GoalSkillEntity(CareerGoalEntity goal, Long skillId, int importance) {
        this.goal = goal;
        this.skillId = skillId;
        this.importance = importance;
    }

    public Long getSkillId() { return skillId; }
    public int getImportance() { return importance; }
}
