package com.skillmap.api.domain.service;

import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.model.JobRecommendation;
import com.skillmap.api.domain.model.JobRecommendation.MissingSkill;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SkillStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Elige las ofertas que mejor encajan con el objetivo y el progreso del usuario. Dominio puro: recibe las
 * habilidades ya detectadas en cada oferta (con SkillMatcher, sin IA) y el catálogo con el estado del usuario.
 *
 * Reglas:
 * - Solo cuentan las ofertas con al menos {@value #MIN_DETECTED_SKILLS} habilidades detectadas y al menos
 *   {@value #MIN_GOAL_SKILLS} del objetivo: con menos, el porcentaje dice poco o la oferta no es de esa ruta.
 * - matchPercentage = % de las habilidades detectadas que el usuario ya tiene: dominada cuenta 1, aprendiendo 0,5
 *   y pendiente 0 (igual que el Analizador).
 * - Orden: mayor encaje, luego más habilidades del objetivo, luego la más reciente.
 */
public final class JobRecommender {

    public static final int MIN_DETECTED_SKILLS = 2;
    public static final int MIN_GOAL_SKILLS = 1;
    private static final double IN_PROGRESS_WEIGHT = 0.5;

    private static final Comparator<JobRecommendation> ORDER = Comparator
            .comparingInt(JobRecommendation::matchPercentage).reversed()
            .thenComparing(Comparator.comparingInt(JobRecommendation::goalSkillCount).reversed())
            .thenComparing(r -> r.posting().publishedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(r -> r.posting().id(), Comparator.nullsLast(Comparator.reverseOrder()));

    private JobRecommender() {
    }

    /**
     * @param detectedSkillIds habilidades del catálogo que aparecen en cada oferta
     * @param catalog          catálogo completo con el estado del usuario; fija el orden de las habilidades
     * @param goalSkillIds     habilidades del objetivo
     * @return como mucho {@code limit}, de la que más encaja a la que menos
     */
    public static List<JobRecommendation> recommend(Collection<JobPosting> postings,
                                                    Function<JobPosting, Set<Long>> detectedSkillIds,
                                                    List<Skill> catalog, Set<Long> goalSkillIds, int limit) {
        return postings.stream()
                .map(posting -> score(posting, detectedSkillIds.apply(posting), catalog, goalSkillIds))
                .filter(Objects::nonNull)
                .sorted(ORDER)
                .limit(limit)
                .toList();
    }

    /** null si la oferta no es relevante para el objetivo. */
    private static JobRecommendation score(JobPosting posting, Set<Long> detected, List<Skill> catalog,
                                           Set<Long> goalSkillIds) {
        List<Skill> skills = catalog.stream().filter(s -> detected.contains(s.getId())).toList();
        int goalSkills = (int) skills.stream().filter(s -> goalSkillIds.contains(s.getId())).count();
        if (skills.size() < MIN_DETECTED_SKILLS || goalSkills < MIN_GOAL_SKILLS) {
            return null;
        }

        List<String> mastered = new ArrayList<>();
        List<MissingSkill> inProgress = new ArrayList<>();
        List<MissingSkill> pending = new ArrayList<>();
        for (Skill skill : skills) {
            switch (skill.getStatus()) {
                case MASTERED -> mastered.add(skill.getName());
                case IN_PROGRESS -> inProgress.add(new MissingSkill(skill.getName(), SkillStatus.IN_PROGRESS));
                case PENDING -> pending.add(new MissingSkill(skill.getName(), SkillStatus.PENDING));
            }
        }
        List<MissingSkill> missing = new ArrayList<>(inProgress);
        missing.addAll(pending);

        int percentage = (int) Math.round((mastered.size() + inProgress.size() * IN_PROGRESS_WEIGHT) * 100 / skills.size());
        return new JobRecommendation(posting, percentage, goalSkills, List.copyOf(mastered), List.copyOf(missing));
    }
}
