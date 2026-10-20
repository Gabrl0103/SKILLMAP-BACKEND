package com.skillmap.api.domain.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Preparación de un usuario en un objetivo en un día. Hay como máximo uno por usuario, objetivo y día:
 * capturedAt es la hora del último cambio de ese día.
 */
public record ReadinessSnapshot(Long id, Long userId, Long goalId, int percentage, LocalDate capturedOn,
                                Instant capturedAt) {
}
