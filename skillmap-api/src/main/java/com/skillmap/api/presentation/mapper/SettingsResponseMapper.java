package com.skillmap.api.presentation.mapper;

import com.skillmap.api.domain.model.AppSettings;
import com.skillmap.api.domain.model.AppSettingsUpdate;
import com.skillmap.api.domain.model.GeminiKeyStatus;
import com.skillmap.api.domain.model.Language;
import com.skillmap.api.domain.model.Theme;
import com.skillmap.api.presentation.dto.SettingsResponse;
import com.skillmap.api.presentation.dto.UpdateSettingsRequest;

public final class SettingsResponseMapper {

    private SettingsResponseMapper() {
    }

    public static SettingsResponse toResponse(AppSettings settings, GeminiKeyStatus gemini) {
        return new SettingsResponse(
                settings.theme().value(),
                settings.showIntro(),
                settings.reduceMotion(),
                settings.language().value(),
                new SettingsResponse.GeminiStatus(gemini.configured(), gemini.source().value())
        );
    }

    /** @throws com.skillmap.api.domain.exception.InvalidSettingsException si el tema o el idioma no son válidos */
    public static AppSettingsUpdate toUpdate(UpdateSettingsRequest request) {
        return new AppSettingsUpdate(
                request.theme() != null ? Theme.fromValue(request.theme()) : null,
                request.showIntro(),
                request.reduceMotion(),
                request.language() != null ? Language.fromValue(request.language()) : null
        );
    }
}
