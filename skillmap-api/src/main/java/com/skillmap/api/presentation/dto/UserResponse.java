package com.skillmap.api.presentation.dto;

/**
 * headline es null si el usuario no tiene titular; goalId, si todavía no eligió objetivo. toString enmascara el correo
 * (Spring registra en DEBUG lo que escribe).
 */
public record UserResponse(Long id, String name, String email, String headline, Long goalId) {

    @Override
    public String toString() {
        return "UserResponse[id=" + id + ", name=" + name + ", email=" + LogMask.email(email) + ", headline=" + headline
                + ", goalId=" + goalId + "]";
    }
}
