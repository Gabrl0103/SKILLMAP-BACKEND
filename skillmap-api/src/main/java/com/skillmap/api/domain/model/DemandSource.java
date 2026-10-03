package com.skillmap.api.domain.model;

/** De dónde sale el porcentaje de demanda de una habilidad. */
public enum DemandSource {
    /** Valor inicial cargado a mano por el DataSeeder. */
    SEEDED,
    /** Calculado a partir de las ofertas de empleo reales guardadas. */
    JOB_POSTINGS
}
