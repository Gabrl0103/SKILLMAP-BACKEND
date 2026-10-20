package com.skillmap.api.application.service.impl;

import com.skillmap.api.domain.exception.AiServiceException;
import com.skillmap.api.domain.exception.InvalidAnalysisHistoryLimitException;
import com.skillmap.api.domain.exception.InvalidJobDescriptionException;
import com.skillmap.api.domain.model.AnalysisHistory;
import com.skillmap.api.domain.model.DemandSource;
import com.skillmap.api.domain.model.JobAnalysis;
import com.skillmap.api.domain.model.JobMatch;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SkillStatus;
import com.skillmap.api.domain.model.UserSkill;
import com.skillmap.api.domain.port.JobSkillExtractor;
import com.skillmap.api.domain.repository.AnalysisHistoryRepository;
import com.skillmap.api.domain.repository.SkillRepository;
import com.skillmap.api.domain.repository.UserSkillRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static final Long USER = 7L;
    private static final Long OTHER_USER = 8L;

    /** El estado que cuenta es el del usuario; el de la tabla de habilidades (el del seed) se ignora. */
    private final UserSkillRepository progress = new UserSkillRepository() {
        @Override public Map<Long, SkillStatus> findStatusesByUserId(Long userId) {
            return USER.equals(userId)
                    ? Map.of(1L, SkillStatus.MASTERED, 2L, SkillStatus.IN_PROGRESS, 4L, SkillStatus.MASTERED)
                    : Map.of();
        }
        @Override public void save(UserSkill userSkill) { }
        @Override public void delete(Long userId, Long skillId) { }
        @Override public void deleteByUserId(Long userId) { }
    };

    private final InMemoryHistory history = new InMemoryHistory();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC);

    private JobAnalyzerServiceImpl serviceReturning(String... skills) {
        return service((description, known) -> List.of(skills));
    }

    private JobAnalyzerServiceImpl service(JobSkillExtractor extractor) {
        return new JobAnalyzerServiceImpl(extractor, new UserSkillCatalog(repository, progress), history, clock);
    }

    @Test
    void usesTheProgressOfTheGivenUser() {
        JobMatch match = serviceReturning("React", "SQL").analyze(OTHER_USER, "oferta").match();

        assertThat(match.mastered()).isEmpty();
        assertThat(match.missing()).containsExactly("React", "SQL");
        assertThat(match.matchPercentage()).isZero();
    }

    @Test
    void classifiesCaseInsensitivelyAndUsesCatalogNames() {
        JobMatch match = serviceReturning("react", "TYPESCRIPT", "Next.js", "GraphQL").analyze(USER, "oferta").match();

        assertThat(match.mastered()).containsExactly("React");
        assertThat(match.inProgress()).containsExactly("TypeScript");
        assertThat(match.missing()).containsExactly("Next.js", "GraphQL");
        // (1 + 0.5) / 4 = 37.5 % -> 38
        assertThat(match.matchPercentage()).isEqualTo(38);
    }

    @Test
    void ignoresDuplicatesFromExtractor() {
        JobMatch match = serviceReturning("SQL", "sql", "React").analyze(USER, "oferta").match();

        assertThat(match.mastered()).containsExactly("SQL", "React");
        assertThat(match.matchPercentage()).isEqualTo(100);
    }

    @Test
    void returnsZeroWhenNoSkillsDetected() {
        JobMatch match = serviceReturning().analyze(USER, "Buscamos persona proactiva").match();

        assertThat(match.matchPercentage()).isZero();
        assertThat(match.mastered()).isEmpty();
        assertThat(match.missing()).isEmpty();
    }

    @Test
    void rejectsBlankDescription() {
        assertThatThrownBy(() -> serviceReturning("React").analyze(USER, "   "))
                .isInstanceOf(InvalidJobDescriptionException.class)
                .hasMessageContaining("vacía");
    }

    @Test
    void rejectsNullDescription() {
        assertThatThrownBy(() -> serviceReturning("React").analyze(USER, null))
                .isInstanceOf(InvalidJobDescriptionException.class);
    }

    @Test
    void rejectsDescriptionOverLimit() {
        String tooLong = "a".repeat(JobAnalyzerServiceImpl.MAX_DESCRIPTION_LENGTH + 1);
        assertThatThrownBy(() -> serviceReturning("React").analyze(USER, tooLong))
                .isInstanceOf(InvalidJobDescriptionException.class)
                .hasMessageContaining("8.000");
    }

    @Test
    void acceptsDescriptionAtLimit() {
        String atLimit = "a".repeat(JobAnalyzerServiceImpl.MAX_DESCRIPTION_LENGTH);
        assertThat(serviceReturning("React").analyze(USER, atLimit).match().matchPercentage()).isEqualTo(100);
    }

    // ---------- Historial y caché ----------

    @Test
    void savesASuccessfulAnalysisWithTitleEngineAndResult() {
        JobAnalysis analysis = service(countingExtractor(new AtomicInteger(), "React", "Kafka"))
                .analyze(USER, "  Desarrollador Frontend\nBuscamos React y Kafka  ");

        assertThat(analysis.engine()).isEqualTo("fake");
        AnalysisHistory saved = history.rows.get(0);
        assertThat(analysis.historyId()).isEqualTo(saved.id());
        assertThat(saved.title()).isEqualTo("Desarrollador Frontend");
        assertThat(saved.jobDescription()).isEqualTo("Desarrollador Frontend\nBuscamos React y Kafka");
        assertThat(saved.engine()).isEqualTo("fake");
        assertThat(saved.result()).isEqualTo(analysis.match());
        assertThat(saved.createdAt()).isEqualTo(clock.instant());
        assertThat(saved.toString()).doesNotContain("Buscamos");
    }

    @Test
    void sameTextAgainReusesTheSkillsWithoutCallingTheAi() {
        AtomicInteger calls = new AtomicInteger();
        JobAnalyzerServiceImpl service = service(countingExtractor(calls, "react", "Kafka"));

        service.analyze(USER, "Buscamos React\ny Kafka");
        JobAnalysis again = service.analyze(USER, "  Buscamos   React y\tKafka ");

        assertThat(calls).hasValue(1);
        assertThat(again.engine()).isEqualTo(JobAnalyzerServiceImpl.CACHE_ENGINE);
        assertThat(again.match().mastered()).containsExactly("React");
        assertThat(again.match().missing()).containsExactly("Kafka");
        // Una sola entrada por oferta: la nueva reemplaza a la anterior.
        assertThat(history.rows).singleElement().extracting(AnalysisHistory::id).isEqualTo(again.historyId());
    }

    @Test
    void theCacheIsPerUser() {
        AtomicInteger calls = new AtomicInteger();
        JobAnalyzerServiceImpl service = service(countingExtractor(calls, "React"));

        service.analyze(USER, "Buscamos React");
        JobAnalysis other = service.analyze(OTHER_USER, "Buscamos React");

        assertThat(calls).hasValue(2);
        assertThat(other.engine()).isEqualTo("fake");
    }

    @Test
    void aFailedAnalysisIsNotSaved() {
        JobAnalyzerServiceImpl service = service((description, known) -> {
            throw new AiServiceException("caída");
        });

        assertThatThrownBy(() -> service.analyze(USER, "Buscamos React")).isInstanceOf(AiServiceException.class);
        assertThatThrownBy(() -> service.analyze(USER, " ")).isInstanceOf(InvalidJobDescriptionException.class);
        assertThat(history.rows).isEmpty();
    }

    @Test
    void anExtractorWithoutEngineIsSavedAsIa() {
        assertThat(serviceReturning("React").analyze(USER, "Buscamos React").engine()).isEqualTo("ia");
    }

    @Test
    void ifSavingFailsTheAnalysisIsStillReturned() {
        history.failOnSave = true;

        JobAnalysis analysis = serviceReturning("React").analyze(USER, "Buscamos React");

        assertThat(analysis.historyId()).isNull();
        assertThat(analysis.match().mastered()).containsExactly("React");
    }

    @Test
    void historyLimitMustBeBetween1And100() {
        JobAnalyzerServiceImpl service = serviceReturning();
        assertThatThrownBy(() -> service.getHistory(USER, 0)).isInstanceOf(InvalidAnalysisHistoryLimitException.class)
                .hasMessage("El parámetro 'limit' debe estar entre 1 y 100.");
        assertThatThrownBy(() -> service.getHistory(USER, 101)).isInstanceOf(InvalidAnalysisHistoryLimitException.class);
        assertThat(service.getHistory(USER, 100)).isEmpty();
    }

    private static JobSkillExtractor countingExtractor(AtomicInteger calls, String... skills) {
        return new JobSkillExtractor() {
            @Override
            public List<String> extractSkills(String jobDescription, Collection<String> knownSkills) {
                calls.incrementAndGet();
                return List.of(skills);
            }

            @Override
            public String engine() {
                return "fake";
            }
        };
    }

    /** Historial en memoria con el mismo contrato que el repositorio JPA. */
    private static final class InMemoryHistory implements AnalysisHistoryRepository {
        final List<AnalysisHistory> rows = new ArrayList<>();
        boolean failOnSave;
        private long nextId = 1;

        @Override public AnalysisHistory save(AnalysisHistory e) {
            if (failOnSave) {
                throw new IllegalStateException("base caída");
            }
            AnalysisHistory saved = new AnalysisHistory(nextId++, e.userId(), e.title(), e.jobDescription(),
                    e.textHash(), e.engine(), e.result(), e.createdAt());
            rows.add(saved);
            return saved;
        }
        @Override public List<AnalysisHistory> findLatest(Long userId, int limit) {
            return rows.stream().filter(r -> r.userId().equals(userId))
                    .sorted(Comparator.comparing(AnalysisHistory::createdAt).thenComparing(AnalysisHistory::id).reversed())
                    .limit(limit).toList();
        }
        @Override public Optional<AnalysisHistory> findByIdAndUserId(Long id, Long userId) {
            return rows.stream().filter(r -> r.id().equals(id) && r.userId().equals(userId)).findFirst();
        }
        @Override public Optional<AnalysisHistory> findLatestByTextHash(Long userId, String textHash) {
            return findLatest(userId, Integer.MAX_VALUE).stream().filter(r -> r.textHash().equals(textHash)).findFirst();
        }
        @Override public boolean deleteByIdAndUserId(Long id, Long userId) {
            return rows.removeIf(r -> r.id().equals(id) && r.userId().equals(userId));
        }
        @Override public void deleteByTextHashExcept(Long userId, String textHash, Long keepId) {
            rows.removeIf(r -> r.userId().equals(userId) && r.textHash().equals(textHash) && !r.id().equals(keepId));
        }
        @Override public void keepLatest(Long userId, int max) {
            rows.removeAll(findLatest(userId, Integer.MAX_VALUE).stream().skip(max).toList());
        }
        @Override public void deleteByUserId(Long userId) {
            rows.removeIf(r -> r.userId().equals(userId));
        }
    }
}
