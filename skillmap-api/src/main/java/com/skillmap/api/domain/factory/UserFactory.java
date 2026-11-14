package com.skillmap.api.domain.factory;

import com.skillmap.api.domain.exception.InvalidUserDataException;
import com.skillmap.api.domain.model.User;
import com.skillmap.api.domain.service.PasswordPolicy;
import com.skillmap.api.domain.service.TextRules;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Crea usuarios válidos:
 * <ul>
 *   <li>Nombre de 2 a 60 caracteres: letras (con tildes y ñ), espacios, guion y apóstrofo. Los espacios de sobra se
 *       quitan (al inicio, al final y los repetidos).</li>
 *   <li>Correo en minúsculas y sin espacios alrededor, con formato válido, máx. 254 caracteres y sin caracteres de
 *       control.</li>
 *   <li>Titular opcional de hasta 80 caracteres, sin caracteres de control.</li>
 *   <li>Contraseña nueva: {@link PasswordPolicy}.</li>
 * </ul>
 * Los mensajes nunca repiten lo recibido.
 */
public final class UserFactory {

    public static final int MIN_NAME_LENGTH = 2;
    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MAX_EMAIL_LOCAL_LENGTH = 64;
    public static final int MAX_HEADLINE_LENGTH = 80;

    public static final String NAME_LENGTH_MESSAGE = "El nombre debe tener entre 2 y 60 caracteres.";
    public static final String NAME_CHARS_MESSAGE = "El nombre solo puede tener letras, espacios, guiones y apóstrofos.";
    public static final String EMAIL_MESSAGE = "El correo no es válido.";
    public static final String HEADLINE_LENGTH_MESSAGE = "El titular puede tener como máximo 80 caracteres.";
    public static final String HEADLINE_CHARS_MESSAGE = "El titular tiene caracteres no permitidos.";

    /** Letras (y sus marcas combinadas: tildes escritas aparte), espacio, guion y apóstrofo recto o tipográfico. */
    private static final Pattern NAME_CHARS = Pattern.compile("^[\\p{L}\\p{M} '’-]+$");
    /** Parte local con los caracteres habituales (RFC 5322 sin comillas) y dominio con etiquetas de letras, números y guion. */
    private static final Pattern EMAIL = Pattern.compile(
            "^[\\p{L}\\p{N}!#$%&'*+/=?^_`{|}~.-]+@[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]*[\\p{L}\\p{N}])?"
                    + "(?:\\.[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]*[\\p{L}\\p{N}])?)+$");

    private UserFactory() {
    }

    public static User createNew(String name, String email, String passwordHash, Instant createdAt) {
        validateName(name);
        validateEmail(email);
        return new User(null, normalizeName(name), normalizeEmail(email), passwordHash, createdAt, null, null);
    }

    public static User reconstruct(Long id, String name, String email, String passwordHash, Instant createdAt,
                                   String headline, Long goalId) {
        return new User(id, name, email, passwordHash, createdAt, headline, goalId);
    }

    public static User reconstruct(Long id, String name, String email, String passwordHash, Instant createdAt,
                                   String headline, Long goalId, Long baseGoalId) {
        return new User(id, name, email, passwordHash, createdAt, headline, goalId, baseGoalId);
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    /** Sin espacios al inicio ni al final y con los repetidos reducidos a uno. */
    public static String normalizeName(String name) {
        return name == null ? "" : TextRules.collapseSpaces(name);
    }

    public static void validateName(String name) {
        String normalized = normalizeName(name);
        int length = TextRules.length(normalized);
        // La columna guarda 60 unidades UTF-16: una letra fuera del plano básico ocupa dos.
        if (length < MIN_NAME_LENGTH || length > MAX_NAME_LENGTH || normalized.length() > MAX_NAME_LENGTH) {
            throw new InvalidUserDataException(NAME_LENGTH_MESSAGE);
        }
        if (!NAME_CHARS.matcher(normalized).matches() || normalized.codePoints().noneMatch(Character::isLetter)) {
            throw new InvalidUserDataException(NAME_CHARS_MESSAGE);
        }
    }

    /** Titular sin espacios de sobra; vacío queda en null (sin titular). */
    public static String normalizeHeadline(String headline) {
        String stripped = headline == null ? "" : headline.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    public static void validateHeadline(String headline) {
        String normalized = normalizeHeadline(headline);
        if (normalized == null) {
            return;
        }
        if (TextRules.length(normalized) > MAX_HEADLINE_LENGTH) {
            throw new InvalidUserDataException(HEADLINE_LENGTH_MESSAGE);
        }
        if (TextRules.hasControlChars(normalized, false)) {
            throw new InvalidUserDataException(HEADLINE_CHARS_MESSAGE);
        }
    }

    public static void validateEmail(String email) {
        String normalized = normalizeEmail(email);
        int at = normalized.indexOf('@');
        String local = at > 0 ? normalized.substring(0, at) : "";
        if (normalized.length() > MAX_EMAIL_LENGTH
                || TextRules.hasControlChars(normalized, false)
                || !EMAIL.matcher(normalized).matches()
                || local.length() > MAX_EMAIL_LOCAL_LENGTH
                || local.startsWith(".") || local.endsWith(".") || local.contains("..")) {
            throw new InvalidUserDataException(EMAIL_MESSAGE);
        }
    }

    /** Contraseña nueva (registro o cambio): ver {@link PasswordPolicy}. */
    public static void validateNewPassword(String password, String email, String name) {
        PasswordPolicy.validate(password, email, name);
    }
}
