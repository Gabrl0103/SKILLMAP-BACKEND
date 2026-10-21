package com.skillmap.api.application.service.impl;

import com.skillmap.api.domain.model.DemandSource;
import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SkillStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JobSkillDetectorTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    private final JobSkillDetector detector = new JobSkillDetector();

    @Test
    void detectsCatalogSkillsWithSkillMatcherRules() {
        JobPosting posting = posting(1, "Java, Spring Boot y PostgreSQL. Nada de JavaScript.");

        Map<Long, Set<Long>> detected = detector.detect(List.of(posting), catalog("Java", "Spring Boot", "SQL", "React"));

        assertThat(detected).containsExactly(Map.entry(1L, Set.of(1L, 2L, 3L)));
    }

    @Test
    void theUserStatusDoesNotMatterAndACatalogChangeRecomputes() {
        JobPosting posting = posting(1, "React y TypeScript");

        assertThat(detector.detect(List.of(posting), catalog("React", "Java")).get(1L)).containsExactly(1L);
        // Mismo catálogo con otro estado: mismo resultado (la caché no depende del usuario)
        List<Skill> mastered = catalog("React", "Java").stream().map(s -> s.withStatus(SkillStatus.MASTERED)).toList();
        assertThat(detector.detect(List.of(posting), mastered).get(1L)).containsExactly(1L);
        // Otro catálogo: se vuelve a detectar con sus nombres
        assertThat(detector.detect(List.of(posting), catalog("Java", "TypeScript")).get(1L)).containsExactly(2L);
    }

    @Test
    void postingsThatAreGoneAreNotReturned() {
        List<Skill> catalog = catalog("Java");
        detector.detect(List.of(posting(1, "Java"), posting(2, "Java")), catalog);

        assertThat(detector.detect(List.of(posting(2, "Java")), catalog)).containsOnlyKeys(2L);
    }

    private static JobPosting posting(long id, String text) {
        return new JobPosting(id, "Remotive", "ext-" + id, "Oferta", "ACME", "Remote", true, "", "https://example.com",
                text, NOW, NOW);
    }

    /** Ids 1, 2, 3… en el orden recibido. */
    private static List<Skill> catalog(String... names) {
        return java.util.stream.IntStream.range(0, names.length)
                .mapToObj(i -> new Skill(i + 1L, names[i], "Cat", 50, SkillStatus.PENDING, DemandSource.SEEDED))
                .toList();
    }
}
