package com.skillmap.api.infrastructure.persistence.mapper;

import com.skillmap.api.domain.factory.CareerGoalFactory;
import com.skillmap.api.domain.model.CareerGoal;
import com.skillmap.api.domain.model.GoalSkill;
import com.skillmap.api.infrastructure.persistence.entity.CareerGoalEntity;

public final class CareerGoalPersistenceMapper {

    private CareerGoalPersistenceMapper() {
    }

    public static CareerGoal toDomain(CareerGoalEntity entity) {
        var skills = entity.getSkills().stream()
                .map(gs -> new GoalSkill(gs.getSkillId(), gs.getImportance()))
                .toList();
        return CareerGoalFactory.reconstruct(entity.getId(), entity.getTitle(), entity.getDescription(), skills);
    }

    public static CareerGoalEntity toEntity(CareerGoal goal) {
        var entity = new CareerGoalEntity(goal.getId(), goal.getTitle(), goal.getDescription());
        goal.getSkills().forEach(gs -> entity.addSkill(gs.skillId(), gs.importance()));
        return entity;
    }
}
