package com.skillmap.api.presentation.dto;

/**
 * PATCH /api/auth/me: solo se cambian los campos presentes (y no null). headline vacío quita el
 * titular.
 */
public record UpdateProfileRequest(String name, String headline) {
}
