package com.skillmap.api.presentation.dto;

/** DELETE /api/goals/custom/{id}: el objetivo elegido después de borrar (otro si se borró el elegido; null si ninguno). */
public record CustomGoalDeletedResponse(Long goalId) {
}
