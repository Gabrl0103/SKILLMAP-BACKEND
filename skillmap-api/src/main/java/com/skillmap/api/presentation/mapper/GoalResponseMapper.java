package com.skillmap.api.presentation.mapper;

import com.skillmap.api.domain.model.CareerGoal;
import com.skillmap.api.domain.model.CustomGoal;
import com.skillmap.api.domain.model.GoalReadiness;
import com.skillmap.api.presentation.dto.GoalResponse;
import com.skillmap.api.presentation.dto.ReadinessResponse;

public final class GoalResponseMapper {

    private GoalResponseMapper() {
    }

    public static GoalResponse toResponse(CareerGoal goal) {
        return new GoalResponse(goal.getId(), goal.getTitle(), goal.getDescription(), skills(goal), goal.isCustom(),
                null, null);
    }

    /** Un objetivo propio con sus fechas, en la misma forma que los base. */
    public static GoalResponse toResponse(CustomGoal goal) {
        CareerGoal asGoal = goal.toCareerGoal();
        return new GoalResponse(goal.id(), goal.name(), goal.description(), skills(asGoal), true, goal.createdAt(),
                goal.updatedAt());
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

    private static java.util.List<GoalResponse.GoalSkillResponse> skills(CareerGoal goal) {
        return goal.getSkills().stream()
                .map(gs -> new GoalResponse.GoalSkillResponse(gs.skillId(), gs.importance()))
                .toList();
    }
}
