package com.skillmap.api.domain.factory;

import com.skillmap.api.domain.exception.InvalidUserDataException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserFactoryTest {

    @ParameterizedTest
    @ValueSource(strings = {"Ana", "Jo", "María José", "Iñaki Núñez", "Jean-Luc", "O'Brien", "D’Angelo", "Zoë Ångström",
            "Łukasz Żółć", "Ana Mari\u0301a"})
    void acceptsNamesWithLettersSpacesHyphensAndApostrophes(String name) {
        assertThatCode(() -> UserFactory.validateName(name)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"<b>Ana</b>", "Ana2", "Ana_Ruiz", "Ana@Ruiz", "Ana\u0000", "Ana\u200B", "😀😀", "--", "Ana; drop"})
    void rejectsTagsDigitsSymbolsAndControlCharacters(String name) {
        assertThatThrownBy(() -> UserFactory.validateName(name))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessage(UserFactory.NAME_CHARS_MESSAGE);
    }

    @Test
    void nameLengthIs2To60AfterCollapsingSpaces() {
        assertThatThrownBy(() -> UserFactory.validateName(" A ")).hasMessage(UserFactory.NAME_LENGTH_MESSAGE);
        assertThatThrownBy(() -> UserFactory.validateName(null)).hasMessage(UserFactory.NAME_LENGTH_MESSAGE);
        assertThatThrownBy(() -> UserFactory.validateName("n".repeat(61))).hasMessage(UserFactory.NAME_LENGTH_MESSAGE);
        assertThatCode(() -> UserFactory.validateName("n".repeat(60))).doesNotThrowAnyException();
        assertThat(UserFactory.normalizeName("  Ana    María  ")).isEqualTo("Ana María");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana@test.local", "  Ana.Ruiz+cenit@Test.Local ", "o'brien@correo.co", "ñandú@correo.es"})
    void acceptsAndNormalizesValidEmails(String email) {
        assertThatCode(() -> UserFactory.validateEmail(email)).doesNotThrowAnyException();
        assertThat(UserFactory.normalizeEmail(email)).isEqualTo(email.strip().toLowerCase());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ana", "ana@", "@test.local", "ana@test", "ana @test.local", "ana@test..local",
            ".ana@test.local", "ana.@test.local", "a..b@test.local", "ana@-test.local", "<ana>@test.local",
            "ana\"x@test.local", "ana\u0007@test.local", "ana\u200B@test.local", "ana@test.local\u0000"})
    void rejectsInvalidEmails(String email) {
        assertThatThrownBy(() -> UserFactory.validateEmail(email))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessage(UserFactory.EMAIL_MESSAGE);
    }

    @Test
    void emailIsAtMost254CharactersAnd64BeforeTheAt() {
        String domain = "@test.local"; // 11
        assertThatCode(() -> UserFactory.validateEmail("a".repeat(64) + domain)).doesNotThrowAnyException();
        assertThatThrownBy(() -> UserFactory.validateEmail("a".repeat(65) + domain))
                .hasMessage(UserFactory.EMAIL_MESSAGE);
        String longDomain = "@" + "d".repeat(60) + "." + "e".repeat(60) + "." + "f".repeat(60) + "." + "g".repeat(10);
        assertThatThrownBy(() -> UserFactory.validateEmail("a".repeat(64) + longDomain))
                .hasMessage(UserFactory.EMAIL_MESSAGE); // 64 + 194 = 258
    }

    @Test
    void headlineRejectsControlCharacters() {
        assertThatThrownBy(() -> UserFactory.validateHeadline("Hola\u0000"))
                .hasMessage(UserFactory.HEADLINE_CHARS_MESSAGE);
        assertThatCode(() -> UserFactory.validateHeadline("Backend <Java> & Spring")).doesNotThrowAnyException();
    }
}
