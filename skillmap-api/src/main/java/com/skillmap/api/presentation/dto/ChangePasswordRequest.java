package com.skillmap.api.presentation.dto;

/** PUT /api/auth/me/password. toString no muestra ninguna de las dos contraseñas. */
public record ChangePasswordRequest(String currentPassword, String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
    }
}
