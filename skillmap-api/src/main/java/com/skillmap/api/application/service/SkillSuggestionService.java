package com.skillmap.api.application.service;

import com.skillmap.api.domain.model.SkillSuggestions;

/** Sugerencias de habilidades para el nombre de un rol ("Otro objetivo"). No guarda nada. */
public interface SkillSuggestionService {

    /**
     * Primero el diccionario de roles (sin IA). Si el rol no coincide con ninguno, la IA que usa el Analizador como
     * respaldo, con tiempo máximo; si falla o tarda, sin sugerencias (source NONE), nunca un error.
     */
    SkillSuggestions suggest(Long userId, String role);
}
