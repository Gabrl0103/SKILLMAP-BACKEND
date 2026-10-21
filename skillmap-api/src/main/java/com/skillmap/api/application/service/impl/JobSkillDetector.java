package com.skillmap.api.application.service.impl;

import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.service.SkillMatcher;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Qué habilidades del catálogo menciona cada oferta guardada, con SkillMatcher (sin IA ni llamadas externas).
 * Una oferta guardada no cambia, así que el resultado se guarda por id: cada petición solo recorre las ofertas
 * nuevas. Si cambia el catálogo (nombres o ids) se empieza de cero; las ofertas que ya no están se olvidan.
 * Solo depende del texto de la oferta, nunca del usuario.
 */
@Component
public class JobSkillDetector {

    /** id → nombre del catálogo con el que se calcularon los matchers y la caché. */
    private Map<Long, String> catalogKey = Map.of();
    private Map<Long, SkillMatcher> matchers = Map.of();
    private Map<Long, Set<Long>> detectedByPosting = new HashMap<>();

    /** @return por id de oferta, los ids de las habilidades que menciona */
    public synchronized Map<Long, Set<Long>> detect(Collection<JobPosting> postings, List<Skill> catalog) {
        Map<Long, String> key = new LinkedHashMap<>();
        catalog.forEach(s -> key.put(s.getId(), s.getName()));
        if (!key.equals(catalogKey)) {
            catalogKey = key;
            matchers = key.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> SkillMatcher.forSkill(e.getValue())));
            detectedByPosting = new HashMap<>();
        }

        Map<Long, Set<Long>> result = new HashMap<>();
        for (JobPosting posting : postings) {
            result.put(posting.id(), detectedByPosting.computeIfAbsent(posting.id(), id -> scan(posting)));
        }
        detectedByPosting.keySet().retainAll(result.keySet());
        return result;
    }

    private Set<Long> scan(JobPosting posting) {
        String text = posting.searchableText();
        return matchers.entrySet().stream()
                .filter(e -> e.getValue().matches(text))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }
}
