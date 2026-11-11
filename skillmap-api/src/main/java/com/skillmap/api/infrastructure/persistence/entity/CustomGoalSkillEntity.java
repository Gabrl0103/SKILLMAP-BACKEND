package com.skillmap.api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Habilidad de un objetivo propio (tabla custom_goal_skills), en el orden en que se eligió (position). */
@Entity
@Table(name = "custom_goal_skills", uniqueConstraints = @UniqueConstraint(
        name = "uk_custom_goal_skills_goal_skill", columnNames = {"goal_id", "skill_id"}))
public class CustomGoalSkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false, foreignKey = @ForeignKey(name = "fk_custom_goal_skills_goal"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CustomGoalEntity goal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false, foreignKey = @ForeignKey(name = "fk_custom_goal_skills_skill"))
    private SkillEntity skill;

    @Column(name = "skill_id", nullable = false, insertable = false, updatable = false)
    private Long skillId;

    @Column(name = "sort_order", nullable = false)
    private int position;

    protected CustomGoalSkillEntity() {
        // Requerido por JPA.
    }

    public CustomGoalSkillEntity(CustomGoalEntity goal, SkillEntity skill, int position) {
        this.goal = goal;
        this.skill = skill;
        this.position = position;
    }

    void moveTo(int newPosition) {
        this.position = newPosition;
    }

    public Long getSkillId() { return skillId != null ? skillId : skill.getId(); }
    public int getPosition() { return position; }
}
