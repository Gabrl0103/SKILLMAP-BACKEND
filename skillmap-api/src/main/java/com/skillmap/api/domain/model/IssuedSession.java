package com.skillmap.api.domain.model;

/**
 * Resultado de registrarse o entrar: el token en claro (se entrega una sola vez) y el usuario.
 * toString no muestra el token.
 */
public record IssuedSession(String token, User user) {

    @Override
    public String toString() {
        return "IssuedSession[userId=" + user.getId() + "]";
    }
}
