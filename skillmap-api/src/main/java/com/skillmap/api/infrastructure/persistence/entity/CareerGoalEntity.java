package com.skillmap.api.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "career_goals")
public class CareerGoalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;

    @OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<GoalSkillEntity> skills = new ArrayList<>();

    protected CareerGoalEntity() {
    }

    public CareerGoalEntity(Long id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    public void addSkill(Long skillId, int importance) {
        skills.add(new GoalSkillEntity(this, skillId, importance));
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<GoalSkillEntity> getSkills() { return skills; }
}
