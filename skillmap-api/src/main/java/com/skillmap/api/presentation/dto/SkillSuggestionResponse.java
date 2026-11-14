package com.skillmap.api.presentation.dto;

import com.skillmap.api.domain.model.SkillSuggestions;

import java.util.List;
import java.util.Locale;

/**
 * Respuesta de POST /api/goals/custom/suggestions. source: "dictionary", "ai" o "none" (sin sugerencias: skills
 * vacía). Cada habilidad: skillId del catálogo, o null si es nueva (se agrega como propia con newSkills al guardar).
 */
public record SkillSuggestionResponse(String source, List<Item> skills) {

    public record Item(Long skillId, String name, String category, boolean custom) {
    }

    public static SkillSuggestionResponse from(SkillSuggestions suggestions) {
        return new SkillSuggestionResponse(suggestions.source().name().toLowerCase(Locale.ROOT),
                suggestions.skills().stream()
                        .map(i -> new Item(i.skillId(), i.name(), i.category(), i.isNew()))
                        .toList());
    }
}
