package com.skillmap.api.presentation.dto;

import com.skillmap.api.domain.service.PasswordPolicy;
import jakarta.validation.constraints.Size;

/** DELETE /api/auth/me: la contraseña confirma el borrado. toString no la muestra. */
public record DeleteAccountRequest(
        @Size(max = 128, message = PasswordPolicy.LENGTH_MESSAGE)
        String password) {

    @Override
    public String toString() {
        return "DeleteAccountRequest[password=***]";
    }
}
