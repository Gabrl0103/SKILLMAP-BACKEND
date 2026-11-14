package com.skillmap.api.presentation.dto;

import com.skillmap.api.domain.factory.UserFactory;
import com.skillmap.api.domain.service.PasswordPolicy;
import com.skillmap.api.presentation.validation.NoControlChars;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/auth/register. toString no muestra la contraseña ni el correo completo (Spring registra el cuerpo leído en DEBUG). */
public record RegisterRequest(
        @NotBlank(message = UserFactory.NAME_LENGTH_MESSAGE)
        @Size(max = 120, message = UserFactory.NAME_LENGTH_MESSAGE)
        @NoControlChars(message = UserFactory.NAME_CHARS_MESSAGE)
        String name,
        @NotBlank(message = UserFactory.EMAIL_MESSAGE)
        @Size(max = UserFactory.MAX_EMAIL_LENGTH, message = UserFactory.EMAIL_MESSAGE)
        @NoControlChars(message = UserFactory.EMAIL_MESSAGE)
        String email,
        @NotNull(message = PasswordPolicy.LENGTH_MESSAGE)
        @Size(max = 128, message = PasswordPolicy.LENGTH_MESSAGE)
        String password) {

    @Override
    public String toString() {
        return "RegisterRequest[name=" + name + ", email=" + LogMask.email(email) + ", password=***]";
    }
}
