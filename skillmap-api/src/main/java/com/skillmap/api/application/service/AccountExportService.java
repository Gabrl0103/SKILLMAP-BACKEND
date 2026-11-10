package com.skillmap.api.application.service;

import com.skillmap.api.domain.model.AccountExport;

/** "Exportar mis datos": reúne en una sola lectura todo lo que la cuenta guarda en el backend. */
public interface AccountExportService {

    AccountExport export(Long userId);
}
