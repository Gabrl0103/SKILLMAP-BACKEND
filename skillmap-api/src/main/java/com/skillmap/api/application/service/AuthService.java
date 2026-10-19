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

    /** Cambia el nombre y/o el titular; un null deja ese campo como estaba. */
    User updateProfile(Long userId, String name, String headline);

    /**
     * Cambia la contraseña si currentPassword es la actual (un fallo cuenta en el mismo límite que el login) y
     * cierra las demás sesiones del usuario; la del token dado sigue abierta.
     */
    void changePassword(Long userId, String token, String currentPassword, String newPassword, String clientIp);

    /** Con la contraseña correcta borra el usuario, sus sesiones, su progreso y sus enlaces y proyectos. Nada global. */
    void deleteAccount(Long userId, String password, String clientIp);
}
