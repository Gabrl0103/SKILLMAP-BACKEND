package com.skillmap.api.presentation.dto;

/** POST /api/auth/login. toString no muestra la contraseña. */
public record LoginRequest(String email, String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
