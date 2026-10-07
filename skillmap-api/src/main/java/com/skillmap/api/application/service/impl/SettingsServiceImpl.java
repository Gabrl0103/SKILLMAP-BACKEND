package com.skillmap.api.application.service.impl;

import com.skillmap.api.application.service.SettingsService;
import com.skillmap.api.domain.model.AppSettings;
import com.skillmap.api.domain.model.AppSettingsUpdate;
import com.skillmap.api.domain.model.GeminiApiKey;
import com.skillmap.api.domain.model.GeminiKeyStatus;
import com.skillmap.api.domain.port.GeminiKeyStore;
import com.skillmap.api.domain.repository.SettingsRepository;
import org.springframework.stereotype.Service;

@Service
public class SettingsServiceImpl implements SettingsService {

    private final SettingsRepository settingsRepository;
    private final GeminiKeyStore geminiKeyStore;

    public SettingsServiceImpl(SettingsRepository settingsRepository, GeminiKeyStore geminiKeyStore) {
        this.settingsRepository = settingsRepository;
        this.geminiKeyStore = geminiKeyStore;
    }

    @Override
    public AppSettings getSettings() {
        return settingsRepository.load();
    }

    /** synchronized: dos cambios parciales a la vez no pueden perder uno de los dos. */
    @Override
    public synchronized AppSettings updateSettings(AppSettingsUpdate update) {
        AppSettings updated = settingsRepository.load().apply(update);
        settingsRepository.save(updated);
        return updated;
    }

    @Override
    public GeminiKeyStatus getGeminiKeyStatus() {
        return new GeminiKeyStatus(geminiKeyStore.activeSource());
    }

    @Override
    public GeminiKeyStatus saveGeminiKey(String rawKey) {
        geminiKeyStore.save(GeminiApiKey.of(rawKey));
        return getGeminiKeyStatus();
    }
}
