package com.skillmap.api.presentation.dto;

import java.time.Instant;

/**
 * DTO de salida. Nunca exponemos la entidad de dominio ni la de persistencia
 * directamente por la API: así el contrato con los 3 clientes (Desktop,
 * Mobile, Wearable) no se rompe si cambia algo interno.
 *
 * custom: true en las habilidades propias del usuario (goalId: su objetivo propio; categoría "Personalizada"). No
 * tienen datos de mercado: demandSource es "NONE" y demandPercentage 0, que no debe mostrarse como un porcentaje.
 * Los campos nuevos solo se agregan al final: los clientes de antes siguen leyendo los demás igual.
 */
public record SkillResponse(
        Long id,
        String name,
        String category,
        int demandPercentage,
        String demandSource,
        String status,
        /** Cuándo pasó el usuario a este estado (ISO-8601, UTC); null si está pendiente. */
        Instant statusUpdatedAt,
        boolean custom,
        Long goalId
) {

    /** demandSource de una habilidad sin datos de mercado (las propias). */
    public static final String NO_DEMAND = "NONE";

    public SkillResponse(Long id, String name, String category, int demandPercentage, String demandSource,
                         String status, Instant statusUpdatedAt) {
        this(id, name, category, demandPercentage, demandSource, status, statusUpdatedAt, false, null);
    }
}
