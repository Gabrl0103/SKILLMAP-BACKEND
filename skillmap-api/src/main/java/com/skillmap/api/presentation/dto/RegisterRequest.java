package com.skillmap.api.presentation.dto;

/** POST /api/auth/register. toString no muestra la contraseña (Spring registra el cuerpo leído en DEBUG). */
public record RegisterRequest(String name, String email, String password) {

    @Override
    public String toString() {
        return "RegisterRequest[name=" + name + ", email=" + email + ", password=***]";
    }
}
