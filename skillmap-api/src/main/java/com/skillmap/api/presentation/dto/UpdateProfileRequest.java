package com.skillmap.api.presentation.dto;

import com.skillmap.api.domain.factory.UserFactory;
import com.skillmap.api.presentation.validation.NoControlChars;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/auth/me: solo se cambian los campos presentes (y no null). headline vacío quita el
 * titular.
 */
public record UpdateProfileRequest(
        @Size(max = 120, message = UserFactory.NAME_LENGTH_MESSAGE)
        @NoControlChars(message = UserFactory.NAME_CHARS_MESSAGE)
        String name,
        @Size(max = 200, message = UserFactory.HEADLINE_LENGTH_MESSAGE)
        @NoControlChars(message = UserFactory.HEADLINE_CHARS_MESSAGE)
        String headline) {
}
