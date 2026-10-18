package com.skillmap.api.presentation.dto;

/** headline es null si el usuario no tiene titular. */
public record UserResponse(Long id, String name, String email, String headline) {
}
