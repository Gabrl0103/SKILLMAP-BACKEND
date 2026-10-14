package com.skillmap.api.domain.factory;

import com.skillmap.api.domain.exception.InvalidUserDataException;
import com.skillmap.api.domain.model.User;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Crea usuarios válidos: nombre de 1 a 60 caracteres, correo válido (máx. 254, en minúsculas)
 * y contraseña de 8 a 72 caracteres (72 bytes es el límite de BCrypt). Los mensajes nunca
 * repiten lo recibido.
 */
public final class UserFactory {

    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 72;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    private UserFactory() {
    }

    public static User createNew(String name, String email, String passwordHash, Instant createdAt) {
        validateName(name);
        validateEmail(email);
        return new User(null, name.strip(), normalizeEmail(email), passwordHash, createdAt);
    }

    public static User reconstruct(Long id, String name, String email, String passwordHash, Instant createdAt) {
        return new User(id, name, email, passwordHash, createdAt);
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    public static void validateName(String name) {
        int length = name == null ? 0 : name.strip().codePointCount(0, name.strip().length());
        if (length < 1 || length > MAX_NAME_LENGTH) {
            throw new InvalidUserDataException("El nombre debe tener entre 1 y 60 caracteres.");
        }
    }

    public static void validateEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(normalized).matches()) {
            throw new InvalidUserDataException("El correo no es válido.");
        }
    }

    public static void validatePassword(String password) {
        int length = password == null ? 0 : password.codePointCount(0, password.length());
        if (length < MIN_PASSWORD_LENGTH || length > MAX_PASSWORD_LENGTH) {
            throw new InvalidUserDataException("La contraseña debe tener entre 8 y 72 caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_LENGTH) {
            throw new InvalidUserDataException(
                    "La contraseña es demasiado larga: con tildes o emojis caben menos de 72 caracteres.");
        }
    }
}
