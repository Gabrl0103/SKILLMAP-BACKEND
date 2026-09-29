package com.skillmap.api.presentation.dto;

import java.util.List;

public record GoalResponse(Long id, String title, String description, List<GoalSkillResponse> skills) {
    public record GoalSkillResponse(Long skillId, int importance) {
    }
}
