package com.skillmap.api.presentation.dto;

import java.util.List;

/** POST y PUT /api/goals/custom: nombre (2–40), descripción opcional (≤ 120) y de 3 a 8 ids de habilidades. */
public record CustomGoalRequest(String name, String description, List<Long> skillIds) {
}
