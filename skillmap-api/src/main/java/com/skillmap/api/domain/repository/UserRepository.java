package com.skillmap.api.domain.repository;

import com.skillmap.api.domain.model.User;

import java.util.Optional;

/** Puerto de salida para las cuentas. El correo se busca ya normalizado (minúsculas). */
public interface UserRepository {
    Optional<User> findById(Long id);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /** Crea (sin id) o actualiza (con id). Lanza EmailAlreadyRegisteredException si el correo ya está en uso. */
    User save(User user);

    void deleteById(Long id);
}
