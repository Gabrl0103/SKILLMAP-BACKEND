package com.skillmap.api.presentation.dto;

/** PUT /api/auth/me/goal: id de un objetivo de GET /api/goals. */
public record UpdateGoalRequest(Long goalId) {
}
