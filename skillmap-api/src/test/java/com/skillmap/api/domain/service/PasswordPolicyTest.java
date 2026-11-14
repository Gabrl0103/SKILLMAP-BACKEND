package com.skillmap.api.domain.service;

import com.skillmap.api.domain.exception.InvalidUserDataException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private static final String EMAIL = "ana.ruiz77@test.local";
    private static final String NAME = "Ana Ruiz";

    private static void assertRejected(String password, String message) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password, EMAIL, NAME))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessage(message);
    }

    @Test
    void acceptsAReasonablePassword() {
        assertThatCode(() -> PasswordPolicy.validate("Ruta-al-cenit-2026", EMAIL, NAME)).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("contraseña con espacios 9", EMAIL, NAME))
                .doesNotThrowAnyException();
    }

    @Test
    void lengthIsCountedInCharactersBetween8And64() {
        assertRejected(null, PasswordPolicy.LENGTH_MESSAGE);
        assertRejected("abc123x", PasswordPolicy.LENGTH_MESSAGE);
        assertThatCode(() -> PasswordPolicy.validate("abc123xy", EMAIL, NAME)).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("a1" + "x".repeat(62), EMAIL, NAME)).doesNotThrowAnyException();
        assertRejected("a1" + "x".repeat(63), PasswordPolicy.LENGTH_MESSAGE);
        // Un emoji es un carácter aunque ocupe dos unidades UTF-16.
        assertThatCode(() -> PasswordPolicy.validate("a1" + "😀".repeat(6), EMAIL, NAME)).doesNotThrowAnyException();
    }

    @Test
    void bcryptLimitIsCheckedInUtf8Bytes() {
        String justFits = "1" + "ñ".repeat(35) + "a"; // 37 caracteres, 72 bytes
        assertThat(justFits.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        assertThatCode(() -> PasswordPolicy.validate(justFits, EMAIL, NAME)).doesNotThrowAnyException();

        String oneByteTooMany = "1" + "ñ".repeat(36); // 37 caracteres, 73 bytes
        assertRejected(oneByteTooMany, PasswordPolicy.BYTES_MESSAGE);
        assertRejected("a1" + "😀".repeat(18), PasswordPolicy.BYTES_MESSAGE); // 20 caracteres, 74 bytes
    }

    @ParameterizedTest
    @ValueSource(strings = {" clave12345", "clave12345 ", "\tclave12345", "clave12345\u00A0"})
    void leadingOrTrailingSpacesAreRejectedNotTrimmed(String password) {
        assertRejected(password, PasswordPolicy.SPACES_MESSAGE);
    }

    @Test
    void controlCharactersAreRejected() {
        assertRejected("clave\u0000123", PasswordPolicy.CONTROL_MESSAGE);
        assertRejected("clave\u200B123", PasswordPolicy.CONTROL_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"solo-letras", "1234567890-", "ÑÑÑÑÑÑÑÑ"})
    void needsALetterAndANumber(String password) {
        assertRejected(password, PasswordPolicy.LETTER_DIGIT_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana.ruiz77@test.local", "ANA.RUIZ77@TEST.LOCAL", "ana.ruiz77"})
    void cannotBeTheEmailOrItsLocalPart(String password) {
        assertRejected(password, PasswordPolicy.PERSONAL_MESSAGE);
    }

    @Test
    void cannotBeTheName() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Pedro 2000", EMAIL, "Pedro2000"))
                .hasMessage(PasswordPolicy.PERSONAL_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"password1", "Password123", "qwerty123", "QWERTY123", "abc12345", "1q2w3e4r", "contraseña1"})
    void commonPasswordsAreRejected(String password) {
        assertRejected(password, PasswordPolicy.COMMON_MESSAGE);
    }

    @Test
    void messagesNeverRepeatThePassword() {
        String secret = "Secreta-De-Prueba-987 ";
        assertThatThrownBy(() -> PasswordPolicy.validate(secret, EMAIL, NAME))
                .message().doesNotContain(secret.strip());
    }
}
