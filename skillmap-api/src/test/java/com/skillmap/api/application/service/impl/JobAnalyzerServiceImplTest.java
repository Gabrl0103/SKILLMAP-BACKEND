package com.skillmap.api.application.service.impl;

import com.skillmap.api.domain.exception.InvalidJobDescriptionException;
import com.skillmap.api.domain.model.DemandSource;
import com.skillmap.api.domain.model.JobMatch;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SkillStatus;
import com.skillmap.api.domain.repository.SkillRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobAnalyzerServiceImplTest {

    private static final List<Skill> CATALOG = List.of(
            new Skill(1L, "React", "Frontend", 95, SkillStatus.MASTERED, DemandSource.SEEDED),
            new Skill(2L, "TypeScript", "Frontend", 88, SkillStatus.IN_PROGRESS, DemandSource.SEEDED),
            new Skill(3L, "Next.js", "Frontend", 71, SkillStatus.PENDING, DemandSource.SEEDED),
            new Skill(4L, "SQL", "Datos", 90, SkillStatus.MASTERED, DemandSource.SEEDED));

    private final SkillRepository repository = new SkillRepository() {
        @Override public List<Skill> findAll() { return CATALOG; }
        @Override public Optional<Skill> findById(Long id) { return Optional.empty(); }
        @Override public Skill save(Skill skill) { return skill; }
    };

    private JobAnalyzerServiceImpl serviceReturning(String... skills) {
        return new JobAnalyzerServiceImpl((description, known) -> List.of(skills), repository);
    }

    @Test
    void classifiesCaseInsensitivelyAndUsesCatalogNames() {
        JobMatch match = serviceReturning("react", "TYPESCRIPT", "Next.js", "GraphQL").analyze("oferta");

        assertThat(match.mastered()).containsExactly("React");
        assertThat(match.inProgress()).containsExactly("TypeScript");
        assertThat(match.missing()).containsExactly("Next.js", "GraphQL");
        // (1 + 0.5) / 4 = 37.5 % -> 38
        assertThat(match.matchPercentage()).isEqualTo(38);
    }

    @Test
    void ignoresDuplicatesFromExtractor() {
        JobMatch match = serviceReturning("SQL", "sql", "React").analyze("oferta");

        assertThat(match.mastered()).containsExactly("SQL", "React");
        assertThat(match.matchPercentage()).isEqualTo(100);
    }

    @Test
    void returnsZeroWhenNoSkillsDetected() {
        JobMatch match = serviceReturning().analyze("Buscamos persona proactiva");

        assertThat(match.matchPercentage()).isZero();
        assertThat(match.mastered()).isEmpty();
        assertThat(match.missing()).isEmpty();
    }

    @Test
    void rejectsBlankDescription() {
        assertThatThrownBy(() -> serviceReturning("React").analyze("   "))
                .isInstanceOf(InvalidJobDescriptionException.class)
                .hasMessageContaining("vacía");
    }

    @Test
    void rejectsNullDescription() {
        assertThatThrownBy(() -> serviceReturning("React").analyze(null))
                .isInstanceOf(InvalidJobDescriptionException.class);
    }

    @Test
    void rejectsDescriptionOverLimit() {
        String tooLong = "a".repeat(JobAnalyzerServiceImpl.MAX_DESCRIPTION_LENGTH + 1);
        assertThatThrownBy(() -> serviceReturning("React").analyze(tooLong))
                .isInstanceOf(InvalidJobDescriptionException.class)
                .hasMessageContaining("8.000");
    }

    @Test
    void acceptsDescriptionAtLimit() {
        String atLimit = "a".repeat(JobAnalyzerServiceImpl.MAX_DESCRIPTION_LENGTH);
        assertThat(serviceReturning("React").analyze(atLimit).matchPercentage()).isEqualTo(100);
    }
}
