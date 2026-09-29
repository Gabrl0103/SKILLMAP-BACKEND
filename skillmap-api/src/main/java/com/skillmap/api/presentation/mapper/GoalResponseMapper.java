package com.skillmap.api.presentation.mapper;

import com.skillmap.api.domain.model.CareerGoal;
import com.skillmap.api.domain.model.GoalReadiness;
import com.skillmap.api.presentation.dto.GoalResponse;
import com.skillmap.api.presentation.dto.ReadinessResponse;

public final class GoalResponseMapper {

    private GoalResponseMapper() {
    }

    public static GoalResponse toResponse(CareerGoal goal) {
        var skills = goal.getSkills().stream()
                .map(gs -> new GoalResponse.GoalSkillResponse(gs.skillId(), gs.importance()))
                .toList();
        return new GoalResponse(goal.getId(), goal.getTitle(), goal.getDescription(), skills);
    }

    public static ReadinessResponse toResponse(GoalReadiness r) {
        return new ReadinessResponse(
                r.goal().getId(),
                r.goal().getTitle(),
                r.readinessPercentage(),
                r.masteredCount(),
                r.totalCount(),
                r.gaps().stream().map(SkillResponseMapper::toResponse).toList()
        );
    }
}
