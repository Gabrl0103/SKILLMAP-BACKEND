package com.skillmap.api.presentation.controller;

import com.skillmap.api.application.service.SettingsService;
import com.skillmap.api.domain.model.GeminiKeySource;
import com.skillmap.api.domain.model.GeminiKeyStatus;
import com.skillmap.api.presentation.dto.GeminiKeyRequest;
import com.skillmap.api.presentation.dto.GeminiKeySaveResponse;
import com.skillmap.api.presentation.dto.SettingsResponse;
import com.skillmap.api.presentation.dto.UpdateSettingsRequest;
import com.skillmap.api.presentation.mapper.SettingsResponseMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pantalla Ajustes: tema, introducción, animaciones y key de Gemini. Los cuerpos están
 * limitados de tamaño (ver RequestBodySizeFilter) y la key nunca sale en una respuesta.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    static final String ENV_WARNING = "La key se guardó, pero la variable de entorno GEMINI_API_KEY tiene "
            + "prioridad y es la que se usa. Para usar la guardada, elimina la variable y reinicia la app.";

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public SettingsResponse getSettings() {
        return SettingsResponseMapper.toResponse(settingsService.getSettings(), settingsService.getGeminiKeyStatus());
    }

    @PatchMapping
    public SettingsResponse updateSettings(@RequestBody UpdateSettingsRequest request) {
        var updated = settingsService.updateSettings(SettingsResponseMapper.toUpdate(request));
        return SettingsResponseMapper.toResponse(updated, settingsService.getGeminiKeyStatus());
    }

    /** 204 si la key guardada es la que se usa; 200 con un aviso si la variable de entorno la tapa. */
    @PutMapping("/gemini-key")
    public ResponseEntity<GeminiKeySaveResponse> saveGeminiKey(@RequestBody GeminiKeyRequest request) {
        GeminiKeyStatus status = settingsService.saveGeminiKey(request.key());
        if (status.source() == GeminiKeySource.ENV) {
            return ResponseEntity.ok(new GeminiKeySaveResponse(true, status.source().value(), ENV_WARNING));
        }
        return ResponseEntity.noContent().build();
    }
}
