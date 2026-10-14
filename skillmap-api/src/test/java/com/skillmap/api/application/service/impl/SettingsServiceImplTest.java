package com.skillmap.api.application.service.impl;

import com.skillmap.api.domain.exception.InvalidSettingsException;
import com.skillmap.api.domain.model.AppSettings;
import com.skillmap.api.domain.model.AppSettingsUpdate;
import com.skillmap.api.domain.model.GeminiApiKey;
import com.skillmap.api.domain.model.GeminiKeySource;
import com.skillmap.api.domain.model.Theme;
import com.skillmap.api.domain.port.GeminiKeyStore;
import com.skillmap.api.domain.repository.SettingsRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsServiceImplTest {

    private final InMemorySettings repository = new InMemorySettings();
    private final FakeKeyStore keyStore = new FakeKeyStore();
    private final SettingsServiceImpl service = new SettingsServiceImpl(repository, keyStore);

    @Test
    void returnsDefaultsWhenNothingSaved() {
        assertThat(service.getSettings()).isEqualTo(AppSettings.DEFAULTS);
    }

    @Test
    void partialUpdateKeepsOtherFieldsAndPersists() {
        service.updateSettings(new AppSettingsUpdate(null, null, true));
        AppSettings updated = service.updateSettings(new AppSettingsUpdate(Theme.DARK, null, null));

        assertThat(updated).isEqualTo(new AppSettings(Theme.DARK, true, true));
        assertThat(repository.stored).isEqualTo(updated);
    }

    @Test
    void savesValidKeyAndReportsSource() {
        var status = service.saveGeminiKey(" test-key-NOT-REAL-0123456789abcdef ");

        assertThat(keyStore.saved).isEqualTo("test-key-NOT-REAL-0123456789abcdef");
        assertThat(status.configured()).isTrue();
        assertThat(status.source()).isEqualTo(GeminiKeySource.FILE);
    }

    @Test
    void reportsEnvironmentWhenItWins() {
        keyStore.env = true;

        var status = service.saveGeminiKey("test-key-NOT-REAL-0123456789abcdef");

        assertThat(keyStore.saved).isNotNull();
        assertThat(status.source()).isEqualTo(GeminiKeySource.ENV);
    }

    @Test
    void invalidKeyIsNotSaved() {
        assertThatThrownBy(() -> service.saveGeminiKey("con espacios dentro de la key"))
                .isInstanceOf(InvalidSettingsException.class);
        assertThat(keyStore.saved).isNull();
        assertThat(service.getGeminiKeyStatus().configured()).isFalse();
    }

    private static final class InMemorySettings implements SettingsRepository {
        AppSettings stored;

        @Override public AppSettings load() { return stored != null ? stored : AppSettings.DEFAULTS; }
        @Override public void save(AppSettings settings) { stored = settings; }
    }

    private static final class FakeKeyStore implements GeminiKeyStore {
        String saved;
        boolean env;

        @Override
        public Optional<String> findKey() {
            return env ? Optional.of("env-key") : Optional.ofNullable(saved);
        }

        @Override
        public GeminiKeySource activeSource() {
            return env ? GeminiKeySource.ENV : saved != null ? GeminiKeySource.FILE : GeminiKeySource.NONE;
        }

        @Override
        public void save(GeminiApiKey key) {
            saved = key.value();
        }
    }
}
