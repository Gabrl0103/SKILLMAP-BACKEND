package com.skillmap.api.presentation.dto;

/** headline es null si el usuario no tiene titular; goalId, si todavía no eligió objetivo. */
public record UserResponse(Long id, String name, String email, String headline, Long goalId) {
}
