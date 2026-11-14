package com.skillmap.api.domain.model;

import java.util.List;

/**
 * Habilidades sugeridas para un rol. source: de dónde salen (diccionario propio, IA de respaldo o ninguna).
 * Cada sugerencia es una habilidad del catálogo (skillId) o una nueva que el usuario puede agregar como propia
 * (skillId null).
 */
public record SkillSuggestions(Source source, List<Item> skills) {

    public enum Source { DICTIONARY, AI, NONE }

    public record Item(Long skillId, String name, String category) {
        public boolean isNew() {
            return skillId == null;
        }
    }

    public SkillSuggestions {
        skills = List.copyOf(skills);
    }

    public static SkillSuggestions none() {
        return new SkillSuggestions(Source.NONE, List.of());
    }
}
