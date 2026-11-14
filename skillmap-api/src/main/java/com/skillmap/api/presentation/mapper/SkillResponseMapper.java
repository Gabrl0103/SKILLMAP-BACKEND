package com.skillmap.api.presentation.mapper;

import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.presentation.dto.SkillResponse;

public final class SkillResponseMapper {

    private SkillResponseMapper() {
    }

    public static SkillResponse toResponse(Skill skill) {
        return new SkillResponse(
                skill.getId(),
                skill.getName(),
                skill.getCategory(),
                skill.getDemandPercentage(),
                skill.hasDemandData() ? skill.getDemandSource().name() : SkillResponse.NO_DEMAND,
                skill.getStatus().name(),
                skill.getStatusUpdatedAt(),
                skill.isCustom(),
                skill.getGoalId()
        );
    }
}
