package com.skillmap.api.presentation.dto;

/** PUT /api/skills/{id}/status: "PENDING", "IN_PROGRESS" o "MASTERED". */
public record UpdateSkillStatusRequest(String status) {
}
