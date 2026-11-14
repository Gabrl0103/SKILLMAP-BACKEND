package com.skillmap.api.presentation.dto;

import java.time.Instant;
import java.util.List;

/**
 * Un objetivo de GET /api/goals. custom: true en los objetivos propios del usuario (sus fechas de creación y edición
 * van en createdAt y updatedAt; en los base son null). Los campos nuevos solo se agregan: los clientes de antes siguen
 * leyendo id, title, description y skills igual.
 */
public record GoalResponse(Long id, String title, String description, List<GoalSkillResponse> skills,
                           boolean custom, Instant createdAt, Instant updatedAt) {

    public GoalResponse(Long id, String title, String description, List<GoalSkillResponse> skills) {
        this(id, title, description, skills, false, null, null);
    }

    public record GoalSkillResponse(Long skillId, int importance) {
    }
}
