package com.skillmap.api.presentation.dto;

import com.skillmap.api.domain.factory.UserFactory;
import com.skillmap.api.domain.service.PasswordPolicy;
import com.skillmap.api.presentation.validation.NoControlChars;
import jakarta.validation.constraints.Size;

/** POST /api/auth/login. toString no muestra la contraseña ni el correo completo. */
public record LoginRequest(
        @Size(max = UserFactory.MAX_EMAIL_LENGTH, message = UserFactory.EMAIL_MESSAGE)
        @NoControlChars(message = UserFactory.EMAIL_MESSAGE)
        String email,
        @Size(max = 128, message = PasswordPolicy.LENGTH_MESSAGE)
        String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + LogMask.email(email) + ", password=***]";
    }
}
