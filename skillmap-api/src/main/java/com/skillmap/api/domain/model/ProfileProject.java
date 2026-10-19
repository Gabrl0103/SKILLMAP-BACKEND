package com.skillmap.api.domain.model;

import java.util.List;

/** Proyecto del perfil de un usuario. description y url pueden ser null; tags nunca (lista vacía). */
public record ProfileProject(Long id, Long userId, String name, String description, String url, List<String> tags) {
}
