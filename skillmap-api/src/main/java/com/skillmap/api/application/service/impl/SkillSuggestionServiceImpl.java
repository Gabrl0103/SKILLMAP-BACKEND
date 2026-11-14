package com.skillmap.api.application.service.impl;

import com.skillmap.api.application.service.SkillSuggestionService;
import com.skillmap.api.domain.exception.InvalidCustomGoalException;
import com.skillmap.api.domain.factory.CustomGoalFactory;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SkillSuggestions;
import com.skillmap.api.domain.model.SkillSuggestions.Item;
import com.skillmap.api.domain.model.SkillSuggestions.Source;
import com.skillmap.api.domain.port.JobSkillExtractor;
import com.skillmap.api.domain.repository.SkillRepository;
import com.skillmap.api.domain.service.RoleSkillDictionary;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Sugerencias para "Otro objetivo":
 * 1. Diccionario de roles (RoleSkillDictionary, sin IA ni red): cubre los roles comunes.
 * 2. Si no coincide, la misma IA que usa el Analizador (JobSkillExtractor: la clave propia del usuario si la guardó, si
 *    no el servicio remoto), con un tiempo máximo (skillmap.goals.suggest-ai-timeout). Cualquier fallo o demora termina
 *    sin sugerencias: el usuario elige o escribe las habilidades a mano. Solo se llama al pulsar "Sugerir".
 * Cada nombre se busca en el catálogo (sin mayúsculas ni tildes); lo que no está se ofrece como habilidad propia nueva.
 * Como mucho 8, sin repetir. El nombre del rol se valida como el de un objetivo propio.
 */
@Service
public class SkillSuggestionServiceImpl implements SkillSuggestionService {

    static final int MAX = RoleSkillDictionary.MAX_SKILLS;
    static final String ROLE_MESSAGE = "Escribe el nombre del rol (de 2 a 40 caracteres) para sugerir habilidades.";
    private static final Logger log = LoggerFactory.getLogger(SkillSuggestionServiceImpl.class);

    private final SkillRepository skills;
    private final JobSkillExtractor extractor;
    private final Duration aiTimeout;
    /** Hilos daemon: una llamada lenta a la IA nunca impide cerrar la app. */
    private final ExecutorService executor = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "skill-suggestions");
        thread.setDaemon(true);
        return thread;
    });

    public SkillSuggestionServiceImpl(SkillRepository skills, JobSkillExtractor extractor,
                                      @Value("${skillmap.goals.suggest-ai-timeout:8s}") Duration aiTimeout) {
        this.skills = skills;
        this.extractor = extractor;
        this.aiTimeout = aiTimeout;
    }

    @Override
    public SkillSuggestions suggest(Long userId, String role) {
        String cleanRole = cleanRole(role);
        Map<String, Skill> catalog = new LinkedHashMap<>();
        skills.findAll().forEach(skill -> catalog.putIfAbsent(RoleSkillDictionary.normalize(skill.getName()), skill));

        List<String> fromDictionary = RoleSkillDictionary.suggest(cleanRole);
        if (!fromDictionary.isEmpty()) {
            return new SkillSuggestions(Source.DICTIONARY, items(fromDictionary, catalog));
        }
        List<Item> fromAi = items(askAi(cleanRole, catalog.values().stream().map(Skill::getName).toList()), catalog);
        return fromAi.isEmpty() ? SkillSuggestions.none() : new SkillSuggestions(Source.AI, fromAi);
    }

    private static String cleanRole(String role) {
        String clean = role == null ? "" : role.strip().replaceAll("\\s+", " ");
        int length = clean.codePointCount(0, clean.length());
        if (length < CustomGoalFactory.MIN_NAME || length > CustomGoalFactory.MAX_NAME
                || clean.chars().anyMatch(c -> c == '<' || c == '>' || Character.isISOControl(c))) {
            throw new InvalidCustomGoalException(ROLE_MESSAGE);
        }
        return clean;
    }

    /** La IA del Analizador, con un texto que describe el puesto. Vacía si falla, tarda o no está configurada. */
    private List<String> askAi(String role, List<String> knownSkills) {
        String description = "Puesto: " + role + ".\n"
                + "Buscamos una persona para el puesto de " + role + ". Requisitos: las habilidades, herramientas y "
                + "conocimientos principales que se necesitan para trabajar como " + role + ".";
        CompletableFuture<List<String>> call = CompletableFuture.supplyAsync(
                () -> extractor.extractSkills(description, knownSkills), executor);
        try {
            List<String> names = call.get(aiTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return names == null ? List.of() : names;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            call.cancel(true);
            return List.of();
        } catch (Exception e) {
            // Sin IA, sin cuota, sin red o demasiado lenta: se sigue sin sugerencias. Sin el rol en el log.
            call.cancel(true);
            log.info("Sugerencias con IA no disponibles: {}", e.getClass().getSimpleName());
            return List.of();
        }
    }

    /** Del catálogo si está (con su id y categoría); si no, nueva (propia). Sin repetir, como mucho 8. */
    private static List<Item> items(List<String> names, Map<String, Skill> catalog) {
        List<Item> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : names) {
            if (items.size() >= MAX) {
                break;
            }
            Skill known = catalog.get(RoleSkillDictionary.normalize(raw));
            String name;
            if (known != null) {
                name = known.getName();
            } else {
                try {
                    name = CustomGoalFactory.skillName(raw);
                } catch (InvalidCustomGoalException e) {
                    continue; // un nombre que no sería válido como habilidad propia no se ofrece
                }
            }
            if (!seen.add(RoleSkillDictionary.normalize(name))) {
                continue;
            }
            items.add(known != null
                    ? new Item(known.getId(), known.getName(), known.getCategory())
                    : new Item(null, name, Skill.CUSTOM_CATEGORY));
        }
        return items;
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }
}
