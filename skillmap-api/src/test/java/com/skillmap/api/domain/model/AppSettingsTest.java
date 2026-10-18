package com.skillmap.api.domain.model;

import com.skillmap.api.domain.exception.InvalidSettingsException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppSettingsTest {

    @Test
    void defaultsAreLightWithIntroAnimationsAndSpanish() {
        assertThat(AppSettings.DEFAULTS).isEqualTo(new AppSettings(Theme.LIGHT, true, false, Language.ES));
    }

    @Test
    void appliesOnlyPresentFields() {
        AppSettings updated = AppSettings.DEFAULTS.apply(new AppSettingsUpdate(Theme.DARK, null, null, null));

        assertThat(updated).isEqualTo(new AppSettings(Theme.DARK, true, false, Language.ES));
        assertThat(updated.apply(new AppSettingsUpdate(null, false, true, Language.EN)))
                .isEqualTo(new AppSettings(Theme.DARK, false, true, Language.EN));
    }

    @Test
    void parsesThemeIgnoringCase() {
        assertThat(Theme.fromValue("light")).isEqualTo(Theme.LIGHT);
        assertThat(Theme.fromValue(" DARK ")).isEqualTo(Theme.DARK);
    }

    @Test
    void rejectsUnknownThemeWithoutEchoingIt() {
        assertThatThrownBy(() -> Theme.fromValue("purple<script>"))
                .isInstanceOf(InvalidSettingsException.class)
                .hasMessage("El tema no es válido. Valores permitidos: light, dark.");
        assertThatThrownBy(() -> Theme.fromValue("")).isInstanceOf(InvalidSettingsException.class);
    }

    @Test
    void parsesLanguageIgnoringCase() {
        assertThat(Language.fromValue("es")).isEqualTo(Language.ES);
        assertThat(Language.fromValue(" EN ")).isEqualTo(Language.EN);
    }

    @Test
    void rejectsUnknownLanguageWithoutEchoingIt() {
        assertThatThrownBy(() -> Language.fromValue("fr<script>"))
                .isInstanceOf(InvalidSettingsException.class)
                .hasMessage("El idioma no es válido. Valores permitidos: es, en.");
        assertThatThrownBy(() -> Language.fromValue(null)).isInstanceOf(InvalidSettingsException.class);
    }
}
