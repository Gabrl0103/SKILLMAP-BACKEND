package com.skillmap.api.presentation.dto;

import java.time.LocalDate;

/** Un punto de la tendencia: día (yyyy-MM-dd) y % de preparación de ese día. */
public record ReadinessPointResponse(LocalDate date, int percentage) {
}
