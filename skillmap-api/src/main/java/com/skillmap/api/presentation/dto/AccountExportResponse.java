package com.skillmap.api.presentation.dto;

import com.skillmap.api.domain.model.AccountExport;
import com.skillmap.api.domain.model.CareerGoal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * GET /api/auth/me/export: el archivo de "Exportar mis datos". format y version identifican el archivo; nombres de
 * habilidades y objetivos van como texto (los ids son internos de este equipo). Sin contraseña, tokens ni keys.
 */
public record AccountExportResponse(
        String format,
        int version,
        Instant exportedAt,
        Account account,
        Goal goal,
        List<SkillState> skills,
        List<Link> links,
        List<Project> projects,
        List<Analysis> analyses,
        List<ReadinessPoint> readinessHistory
) {

    public static final String FORMAT = "cenit-export";
    public static final int VERSION = 1;

    public record Account(String name, String email, String headline, Instant createdAt) {
    }

    public record Goal(String title) {
    }

    public record SkillState(String name, String category, String status, Instant statusUpdatedAt) {
    }

    public record Link(String label, String url) {
    }

    public record Project(String name, String description, String url, List<String> tags) {
    }

    public record Analysis(String title, Instant createdAt, int matchPercentage, List<String> mastered,
                           List<String> inProgress, List<String> missing, String jobDescription) {
    }

    public record ReadinessPoint(String goal, LocalDate date, int percentage) {
    }

    public static AccountExportResponse from(AccountExport data) {
        Map<Long, String> goalTitles = data.goals().stream()
                .collect(Collectors.toMap(CareerGoal::getId, CareerGoal::getTitle, (a, b) -> a));
        return new AccountExportResponse(
                FORMAT,
                VERSION,
                data.exportedAt(),
                new Account(data.name(), data.email(), data.headline(), data.createdAt()),
                data.goal() == null ? null : new Goal(data.goal().getTitle()),
                data.skills().stream()
                        .map(s -> new SkillState(s.getName(), s.getCategory(), s.getStatus().name(), s.getStatusUpdatedAt()))
                        .toList(),
                data.links().stream().map(l -> new Link(l.label(), l.url())).toList(),
                data.projects().stream().map(p -> new Project(p.name(), p.description(), p.url(), p.tags())).toList(),
                data.analyses().stream()
                        .map(a -> new Analysis(a.title(), a.createdAt(), a.result().matchPercentage(),
                                a.result().mastered(), a.result().inProgress(), a.result().missing(), a.jobDescription()))
                        .toList(),
                data.readiness().stream()
                        .map(r -> new ReadinessPoint(Objects.requireNonNullElse(goalTitles.get(r.goalId()), ""), r.capturedOn(), r.percentage()))
                        .toList());
    }
}
