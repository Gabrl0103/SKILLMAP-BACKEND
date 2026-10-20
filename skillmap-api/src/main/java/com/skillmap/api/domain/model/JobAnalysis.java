package com.skillmap.api.domain.model;

/**
 * Resultado de un análisis nuevo. engine: quién extrajo las habilidades ("gemini", "remote") o "cache" si se
 * reutilizaron las de un análisis anterior de la misma oferta. historyId es null si no se pudo guardar.
 */
public record JobAnalysis(JobMatch match, String engine, Long historyId) {
}
