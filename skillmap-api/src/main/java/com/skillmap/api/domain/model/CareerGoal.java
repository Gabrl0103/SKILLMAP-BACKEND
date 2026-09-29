package com.skillmap.api.domain.model;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Objetivo laboral (ej: "Frontend Senior"). Agrupa las habilidades que exige
 * y sabe calcular qué tan cerca está el usuario de alcanzarlo.
 */
public class CareerGoal {

    private final Long id;
    private final String title;
    private final String description;
    private final List<GoalSkill> skills;

    public CareerGoal(Long id, String title, String description, List<GoalSkill> skills) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.skills = List.copyOf(skills);
    }

    /**
     * Regla de negocio: el readiness es ponderado. Dominar una habilidad de
     * importancia 5 acerca más al objetivo que una de importancia 1.
     * Devuelve un entero de 0 a 100.
     */
    public int calculateReadiness(Collection<Skill> allSkills) {
        int totalWeight = skills.stream().mapToInt(GoalSkill::importance).sum();
        if (totalWeight == 0) {
            return 0;
        }
        Set<Long> masteredIds = allSkills.stream()
                .filter(s -> s.getStatus() == SkillStatus.MASTERED)
                .map(Skill::getId)
                .collect(Collectors.toSet());
        int masteredWeight = skills.stream()
                .filter(gs -> masteredIds.contains(gs.skillId()))
                .mapToInt(GoalSkill::importance)
                .sum();
        return Math.round(masteredWeight * 100f / totalWeight);
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<GoalSkill> getSkills() { return skills; }
}
