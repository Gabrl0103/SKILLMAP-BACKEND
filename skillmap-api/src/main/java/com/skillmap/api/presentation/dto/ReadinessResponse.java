package com.skillmap.api.presentation.dto;

import java.util.List;

/** Todo lo que necesita el Dashboard en una sola llamada. */
public record ReadinessResponse(
        Long goalId,
        String goalTitle,
        int readinessPercentage,
        int masteredCount,
        int totalCount,
        List<SkillResponse> gaps
) {
}
