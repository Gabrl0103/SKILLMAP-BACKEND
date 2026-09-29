package com.skillmap.api.domain.factory;

import com.skillmap.api.domain.model.CareerGoal;
import com.skillmap.api.domain.model.GoalSkill;

import java.util.HashSet;
import java.util.List;

/** Factory de CareerGoal: valida título, importancia (1-5) y que no haya habilidades repetidas. */
public final class CareerGoalFactory {

    private CareerGoalFactory() {
    }

    public static CareerGoal createNew(String title, String description, List<GoalSkill> skills) {
        return reconstruct(null, title, description, skills);
    }

    public static CareerGoal reconstruct(Long id, String title, String description, List<GoalSkill> skills) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("El título del objetivo no puede estar vacío");
        }
        var seen = new HashSet<Long>();
        for (GoalSkill gs : skills) {
            if (gs.importance() < 1 || gs.importance() > 5) {
                throw new IllegalArgumentException("La importancia debe estar entre 1 y 5");
            }
            if (!seen.add(gs.skillId())) {
                throw new IllegalArgumentException("La habilidad " + gs.skillId() + " está repetida en el objetivo");
            }
        }
        return new CareerGoal(id, title, description, skills);
    }
}
