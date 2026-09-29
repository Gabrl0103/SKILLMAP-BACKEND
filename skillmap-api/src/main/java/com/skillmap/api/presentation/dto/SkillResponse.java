package com.skillmap.api.presentation.dto;

/**
 * DTO de salida. Nunca exponemos la entidad de dominio ni la de persistencia
 * directamente por la API: así el contrato con los 3 clientes (Desktop,
 * Mobile, Wearable) no se rompe si cambia algo interno.
 */
public record SkillResponse(
        Long id,
        String name,
        String category,
        int demandPercentage,
        String status
) {
}
