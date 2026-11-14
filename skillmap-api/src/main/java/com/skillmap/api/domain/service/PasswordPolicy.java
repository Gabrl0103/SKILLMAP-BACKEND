package com.skillmap.api.domain.service;

import com.skillmap.api.domain.exception.InvalidUserDataException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Política de contraseñas nuevas (registro y cambio de contraseña). Las contraseñas de cuentas ya creadas no se
 * revisan al iniciar sesión: una cuenta antigua con una contraseña que hoy no pasaría sigue entrando.
 * <ul>
 *   <li>8 a 64 caracteres y como mucho 72 bytes en UTF-8 (BCrypt ignora lo que pasa de 72 bytes: dos contraseñas
 *       distintas que comparten esos 72 bytes darían el mismo hash).</li>
 *   <li>Al menos una letra y un número.</li>
 *   <li>Sin espacios al inicio ni al final: se rechaza, no se recorta (recortar en silencio cambiaría la contraseña que
 *       la persona cree tener) y sin caracteres de control.</li>
 *   <li>Distinta del correo, de la parte del correo antes de la @ y del nombre.</li>
 *   <li>Fuera de una lista corta de contraseñas comunes.</li>
 * </ul>
 * Los mensajes nunca repiten la contraseña.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;
    public static final int MAX_BYTES = 72;

    public static final String LENGTH_MESSAGE = "La contraseña debe tener entre 8 y 64 caracteres.";
    public static final String BYTES_MESSAGE =
            "La contraseña es demasiado larga: con tildes o emojis caben menos de 64 caracteres.";
    public static final String SPACES_MESSAGE = "La contraseña no puede empezar ni terminar con espacios.";
    public static final String CONTROL_MESSAGE = "La contraseña tiene caracteres no permitidos.";
    public static final String LETTER_DIGIT_MESSAGE = "La contraseña debe incluir al menos una letra y un número.";
    public static final String PERSONAL_MESSAGE = "La contraseña no puede ser igual a tu correo ni a tu nombre.";
    public static final String COMMON_MESSAGE = "Esa contraseña es muy común. Elige otra más difícil de adivinar.";

    /** Comparadas sin distinguir mayúsculas. Cortas a propósito: solo las que primero prueba cualquier ataque. */
    private static final Set<String> COMMON = Set.of(
            "12345678", "123456789", "1234567890", "password", "password1", "password123", "passw0rd",
            "qwerty123", "qwertyuiop", "qwerty12", "1q2w3e4r", "1qaz2wsx", "abc12345", "abcd1234", "iloveyou1",
            "admin123", "welcome1", "letmein1", "11111111", "00000000", "87654321", "contraseña", "contraseña1",
            "contrasena1", "contraseña123", "clave123", "12341234", "asdf1234", "zaq12wsx", "superman1",
            "skillmap1", "cenit123", "teamo123");

    private PasswordPolicy() {
    }

    public static void validate(String password, String email, String name) {
        int length = TextRules.length(password);
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new InvalidUserDataException(LENGTH_MESSAGE);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidUserDataException(BYTES_MESSAGE);
        }
        if (isSpace(password.codePointAt(0)) || isSpace(password.codePointBefore(password.length()))) {
            throw new InvalidUserDataException(SPACES_MESSAGE);
        }
        if (TextRules.hasControlChars(password, false)) {
            throw new InvalidUserDataException(CONTROL_MESSAGE);
        }
        boolean letter = password.codePoints().anyMatch(Character::isLetter);
        boolean digit = password.codePoints().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            throw new InvalidUserDataException(LETTER_DIGIT_MESSAGE);
        }
        if (isPersonal(password, email, name)) {
            throw new InvalidUserDataException(PERSONAL_MESSAGE);
        }
        if (COMMON.contains(password.toLowerCase(Locale.ROOT))) {
            throw new InvalidUserDataException(COMMON_MESSAGE);
        }
    }

    /** Espacio normal, tabulador, salto de línea y también el espacio duro (U+00A0) y los demás separadores. */
    private static boolean isSpace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }

    private static boolean isPersonal(String password, String email, String name) {
        String candidate = compact(password);
        String normalizedEmail = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        int at = normalizedEmail.indexOf('@');
        String localPart = at > 0 ? normalizedEmail.substring(0, at) : "";
        return candidate.equals(compact(normalizedEmail))
                || (!localPart.isEmpty() && candidate.equals(compact(localPart)))
                || (name != null && !name.isBlank() && candidate.equals(compact(name)));
    }

    /** En minúsculas y sin espacios: "Ana Lopez" y "analopez" cuentan como iguales. */
    private static String compact(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
