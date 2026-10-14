package com.skillmap.api.application.service;

import com.skillmap.api.domain.model.IssuedSession;
import com.skillmap.api.domain.model.User;

import java.util.Optional;

/** Casos de uso de cuentas y sesiones. */
public interface AuthService {
    IssuedSession register(String name, String email, String password);

    /** clientIp se usa para limitar los intentos fallidos por correo + IP. */
    IssuedSession login(String email, String password, String clientIp);

    void logout(String token);

    /** Id del usuario si el token corresponde a una sesión vigente. */
    Optional<Long> authenticate(String token);

    User getUser(Long userId);
}
