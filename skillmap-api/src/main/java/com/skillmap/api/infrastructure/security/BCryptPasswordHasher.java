package com.skillmap.api.infrastructure.security;

import com.skillmap.api.domain.port.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Adaptador de PasswordHasher con BCrypt (spring-security-crypto, sin Spring Security completo). */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    /** BCrypt solo usa los primeros 72 bytes: más largo no se acepta ni se compara. */
    private static final int MAX_BYTES = 72;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        if (tooLong(rawPassword)) {
            throw new IllegalArgumentException("La contraseña supera el máximo de 72 bytes.");
        }
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || tooLong(rawPassword)) {
            return false;
        }
        return encoder.matches(rawPassword, passwordHash);
    }

    private static boolean tooLong(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }
}
