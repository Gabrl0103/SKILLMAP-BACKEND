package com.skillmap.api.presentation.dto;

/**
 * Respuesta de registro y login: la única vez que el token sale del servidor.
 * toString no lo muestra (Spring registra en DEBUG lo que escribe).
 */
public record AuthResponse(String token, UserResponse user) {

    @Override
    public String toString() {
        return "AuthResponse[token=***, user=" + user + "]";
    }
}
