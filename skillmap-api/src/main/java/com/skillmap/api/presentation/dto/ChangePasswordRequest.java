package com.skillmap.api.presentation.dto;

import com.skillmap.api.domain.service.PasswordPolicy;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PUT /api/auth/me/password. toString no muestra ninguna de las dos contraseñas. */
public record ChangePasswordRequest(
        @Size(max = 128, message = PasswordPolicy.LENGTH_MESSAGE)
        String currentPassword,
        @NotNull(message = PasswordPolicy.LENGTH_MESSAGE)
        @Size(max = 128, message = PasswordPolicy.LENGTH_MESSAGE)
        String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
    }
}
