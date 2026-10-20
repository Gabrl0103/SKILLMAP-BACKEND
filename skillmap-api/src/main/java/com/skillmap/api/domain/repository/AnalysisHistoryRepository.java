package com.skillmap.api.domain.repository;

import com.skillmap.api.domain.model.AnalysisHistory;

import java.util.List;
import java.util.Optional;

/** Puerto de salida para el historial del Analizador. Toda consulta va filtrada por usuario. */
public interface AnalysisHistoryRepository {

    /** Crea el registro (id null). */
    AnalysisHistory save(AnalysisHistory entry);

    /** Los más recientes primero, como mucho limit. */
    List<AnalysisHistory> findLatest(Long userId, int limit);

    Optional<AnalysisHistory> findByIdAndUserId(Long id, Long userId);

    /** El más reciente del usuario con ese texto (por su hash): la caché del Analizador. */
    Optional<AnalysisHistory> findLatestByTextHash(Long userId, String textHash);

    /** true si existía y era del usuario. */
    boolean deleteByIdAndUserId(Long id, Long userId);

    /** Los del usuario con ese texto salvo keepId: la misma oferta aparece una sola vez. */
    void deleteByTextHashExcept(Long userId, String textHash, Long keepId);

    /** Deja solo los max más recientes del usuario. */
    void keepLatest(Long userId, int max);

    /** Todos los del usuario ("Borrar historial" y al eliminar su cuenta). */
    void deleteByUserId(Long userId);
}
