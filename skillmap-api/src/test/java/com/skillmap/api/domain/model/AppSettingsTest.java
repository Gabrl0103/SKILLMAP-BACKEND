package com.skillmap.api.domain.model;

import com.skillmap.api.domain.exception.InvalidSettingsException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppSettingsTest {

    @Test
    void defaultsAreLightWithIntroAndAnimations() {
        assertThat(AppSettings.DEFAULTS).isEqualTo(new AppSettings(Theme.LIGHT, true, false));
    }

    @Test
    void appliesOnlyPresentFields() {
        AppSettings updated = AppSettings.DEFAULTS.apply(new AppSettingsUpdate(Theme.DARK, null, null));

        assertThat(updated).isEqualTo(new AppSettings(Theme.DARK, true, false));
        assertThat(updated.apply(new AppSettingsUpdate(null, false, true)))
                .isEqualTo(new AppSettings(Theme.DARK, false, true));
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
}
