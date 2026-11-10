package com.skillmap.api.presentation.controller;

import com.skillmap.api.application.service.AccountExportService;
import com.skillmap.api.presentation.dto.AccountExportResponse;
import com.skillmap.api.presentation.filter.AuthenticationFilter;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Exportar mis datos" (Ajustes > Datos y cuenta): GET /api/auth/me/export con sesión (AuthenticationFilter). Solo
 * lee; el frontend guarda la respuesta como archivo JSON (en el .exe, con el diálogo del sistema).
 */
@RestController
public class AccountExportController {

    private final AccountExportService exportService;

    public AccountExportController(AccountExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/api/auth/me/export")
    public ResponseEntity<AccountExportResponse> export(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AccountExportResponse.from(exportService.export(userId)));
    }
}
