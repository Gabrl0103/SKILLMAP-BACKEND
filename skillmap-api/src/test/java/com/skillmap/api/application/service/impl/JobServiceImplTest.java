package com.skillmap.api.application.service.impl;

import com.skillmap.api.domain.exception.JobSourceRateLimitException;
import com.skillmap.api.domain.exception.JobSourceUnavailableException;
import com.skillmap.api.domain.model.DemandSource;
import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.model.JobStats;
import com.skillmap.api.domain.model.JobSyncReport;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SkillStatus;
import com.skillmap.api.domain.model.SourceSyncResult;
import com.skillmap.api.domain.model.SourceSyncStatus;
import com.skillmap.api.domain.port.JobPostingSource;
import com.skillmap.api.domain.repository.JobPostingRepository;
import com.skillmap.api.domain.repository.JobSyncStateRepository;
import com.skillmap.api.domain.repository.SkillRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    private static final Duration MIN_INTERVAL = Duration.ofHours(6);
    private static final Duration MAX_AGE = Duration.ofDays(60);
    private static final int MAX_POSTINGS = 1000;
    private static final int MIN_FOR_DEMAND = 30;

    private final InMemoryPostings postings = new InMemoryPostings();
    private final InMemorySyncState syncState = new InMemorySyncState();
    private final InMemorySkills skills = new InMemorySkills(
            new Skill(1L, "Java", "Backend", 82, SkillStatus.IN_PROGRESS, DemandSource.SEEDED),
            new Skill(2L, "JavaScript", "Frontend", 70, SkillStatus.PENDING, DemandSource.SEEDED),
            new Skill(3L, "Estadística", "Datos", 66, SkillStatus.PENDING, DemandSource.SEEDED));

    private JobServiceImpl service(JobPostingSource... sources) {
        return service(MAX_POSTINGS, sources);
    }

    private JobServiceImpl service(int maxPostings, JobPostingSource... sources) {
        return new JobServiceImpl(List.of(sources), postings, syncState, skills,
                Clock.fixed(NOW, ZoneOffset.UTC), MIN_INTERVAL, MAX_AGE, maxPostings, MIN_FOR_DEMAND);
    }

    // ---------- Límite de frecuencia por fuente ----------

    @Test
    void skipsSourceSyncedLessThanSixHoursAgoWithoutCallingIt() {
        Instant lastSync = NOW.minus(Duration.ofHours(5).plusMinutes(59));
        syncState.saveLastSync("Remotive", lastSync);
        FakeSource remotive = new FakeSource("Remotive", () -> postings("Remotive", 5, "Java"));

        JobSyncReport report = service(remotive).sync();

        assertThat(remotive.calls).isZero();
        SourceSyncResult result = report.sources().get(0);
        assertThat(result.status()).isEqualTo(SourceSyncStatus.SKIPPED);
        assertThat(result.lastSyncAt()).isEqualTo(lastSync);
        assertThat(result.message())
                .contains("Remotive")
                .contains("2026-10-02 06:01 UTC")
                .contains("6 horas")
                .contains("2026-10-02 12:01 UTC");
        assertThat(syncState.findLastSync("Remotive")).contains(lastSync);
    }

    @Test
    void syncsSourceOnceSixHoursHavePassed() {
        syncState.saveLastSync("Remotive", NOW.minus(MIN_INTERVAL));
        FakeSource remotive = new FakeSource("Remotive", () -> postings("Remotive", 3, "Java"));

        SourceSyncResult result = service(remotive).sync().sources().get(0);

        assertThat(remotive.calls).isEqualTo(1);
        assertThat(result.status()).isEqualTo(SourceSyncStatus.SYNCED);
        assertThat(result.added()).isEqualTo(3);
        assertThat(syncState.findLastSync("Remotive")).contains(NOW);
    }

    @Test
    void skipIsPerSource() {
        syncState.saveLastSync("Remotive", NOW.minus(Duration.ofHours(1)));
        FakeSource remotive = new FakeSource("Remotive", () -> postings("Remotive", 3, "Java"));
        FakeSource arbeitnow = new FakeSource("Arbeitnow", () -> postings("Arbeitnow", 4, "Java"));

        JobSyncReport report = service(remotive, arbeitnow).sync();

        assertThat(report.sources()).extracting(SourceSyncResult::status)
                .containsExactly(SourceSyncStatus.SKIPPED, SourceSyncStatus.SYNCED);
        assertThat(remotive.calls).isZero();
        assertThat(arbeitnow.calls).isEqualTo(1);
    }

    @Test
    void secondManualSyncRightAfterFirstIsSkipped() {
        FakeSource remotive = new FakeSource("Remotive", () -> postings("Remotive", 3, "Java"));
        JobServiceImpl service = service(remotive);

        service.sync();
        JobSyncReport second = service.sync();

        assertThat(remotive.calls).isEqualTo(1);
        assertThat(second.sources().get(0).status()).isEqualTo(SourceSyncStatus.SKIPPED);
    }

    // ---------- Guardado y retención ----------

    @Test
    void storesOnlyNewPostings() {
        postings.saveAll(postings("Remotive", 2, "Java"));
        FakeSource remotive = new FakeSource("Remotive", () -> postings("Remotive", 5, "Java"));

        SourceSyncResult result = service(remotive).sync().sources().get(0);

        assertThat(result.fetched()).isEqualTo(5);
        assertThat(result.added()).isEqualTo(3);
        assertThat(postings.count()).isEqualTo(5);
    }

    @Test
    void sameExternalIdInDifferentSourcesIsNotADuplicate() {
        FakeSource remotive = new FakeSource("Remotive", () -> postings("Remotive", 2, "Java"));
        FakeSource arbeitnow = new FakeSource("Arbeitnow", () -> postings("Arbeitnow", 2, "Java"));

        service(remotive, arbeitnow).sync();

        assertThat(postings.count()).isEqualTo(4);
    }

    @Test
    void deletesPostingsOlderThanSixtyDaysAndSkipsOldIncoming() {
        postings.saveAll(List.of(posting("Remotive", "old", "Java", NOW.minus(Duration.ofDays(61)))));
        FakeSource remotive = new FakeSource("Remotive", () -> List.of(
                posting("Remotive", "fresh", "Java", NOW.minus(Duration.ofDays(59))),
                posting("Remotive", "stale", "Java", NOW.minus(Duration.ofDays(70)))));

        SourceSyncResult result = service(remotive).sync().sources().get(0);

        assertThat(result.added()).isEqualTo(1);
        assertThat(postings.findAll()).extracting(JobPosting::externalId).containsExactly("fresh");
    }

    @Test
    void keepsOnlyTheNewestUpToTheMaximum() {
        FakeSource remotive = new FakeSource("Remotive", () -> postings("Remotive", 8, "Java"));

        JobSyncReport report = service(5, remotive).sync();

        assertThat(report.totalPostings()).isEqualTo(5);
        // postings() publica la i-ésima i horas antes de NOW: sobreviven las 5 más recientes.
        assertThat(postings.findAll()).extracting(JobPosting::externalId)
                .containsExactlyInAnyOrder("Remotive-0", "Remotive-1", "Remotive-2", "Remotive-3", "Remotive-4");
    }

    // ---------- Demanda ----------

    @Test
    void recalculatesDemandWithThirtyOrMorePostings() {
        List<JobPosting> batch = new ArrayList<>();
        batch.addAll(postings("Remotive", 15, "Java and Spring"));
        batch.addAll(postings("Arbeitnow", 10, "JavaScript and React"));
        batch.addAll(postings("Otra", 5, "Statistics and Python"));
        postings.saveAll(batch.subList(0, 25));
        FakeSource source = new FakeSource("Otra", () -> batch.subList(25, 30));

        JobSyncReport report = service(source).sync();

        assertThat(report.demandRecalculated()).isTrue();
        assertThat(report.totalPostings()).isEqualTo(30);
        // Java: 15/30 = 50 %; JavaScript: 10/30 = 33 %; Estadística (vía "statistics"): 5/30 = 17 %.
        assertThat(skills.byName("Java").getDemandPercentage()).isEqualTo(50);
        assertThat(skills.byName("JavaScript").getDemandPercentage()).isEqualTo(33);
        assertThat(skills.byName("Estadística").getDemandPercentage()).isEqualTo(17);
        assertThat(skills.findAll()).extracting(Skill::getDemandSource).containsOnly(DemandSource.JOB_POSTINGS);
    }

    @Test
    void keepsPreviousDemandWithFewerThanThirtyPostings() {
        FakeSource remotive = new FakeSource("Remotive", () -> postings("Remotive", 29, "Java"));

        JobSyncReport report = service(remotive).sync();

        assertThat(report.demandRecalculated()).isFalse();
        assertThat(skills.byName("Java").getDemandPercentage()).isEqualTo(82);
        assertThat(skills.byName("Java").getDemandSource()).isEqualTo(DemandSource.SEEDED);
    }

    @Test
    void skillNotMentionedAnywhereDropsToZero() {
        FakeSource remotive = new FakeSource("Remotive", () -> postings("Remotive", 30, "Go and Rust"));

        service(remotive).sync();

        assertThat(skills.byName("Java").getDemandPercentage()).isZero();
        assertThat(skills.byName("Java").getDemandSource()).isEqualTo(DemandSource.JOB_POSTINGS);
    }

    // ---------- Errores ----------

    @Test
    void oneFailingSourceDoesNotStopTheOthers() {
        FakeSource remotive = new FakeSource("Remotive", () -> {
            throw new JobSourceRateLimitException("Remotive alcanzó su límite de uso.");
        });
        FakeSource arbeitnow = new FakeSource("Arbeitnow", () -> postings("Arbeitnow", 2, "Java"));

        JobSyncReport report = service(remotive, arbeitnow).sync();

        SourceSyncResult failed = report.sources().get(0);
        assertThat(failed.status()).isEqualTo(SourceSyncStatus.FAILED);
        assertThat(failed.message()).isEqualTo("Remotive alcanzó su límite de uso.");
        assertThat(report.sources().get(1).status()).isEqualTo(SourceSyncStatus.SYNCED);
        assertThat(syncState.findLastSync("Remotive")).isEmpty();
    }

    @Test
    void throwsWhenEverySourceFails() {
        FakeSource remotive = new FakeSource("Remotive", () -> {
            throw new JobSourceUnavailableException("Remotive no está disponible en este momento.");
        });
        FakeSource arbeitnow = new FakeSource("Arbeitnow", () -> {
            throw new JobSourceRateLimitException("Arbeitnow alcanzó su límite de uso.");
        });

        assertThatThrownBy(() -> service(remotive, arbeitnow).sync())
                .isInstanceOf(JobSourceUnavailableException.class)
                .hasMessageContaining("Remotive no está disponible");
    }

    @Test
    void failedSourceCanBeRetriedImmediately() {
        int[] attempt = {0};
        FakeSource remotive = new FakeSource("Remotive", () -> {
            if (attempt[0]++ == 0) {
                throw new JobSourceUnavailableException("caída");
            }
            return postings("Remotive", 1, "Java");
        });
        FakeSource arbeitnow = new FakeSource("Arbeitnow", () -> postings("Arbeitnow", 1, "Java"));
        JobServiceImpl service = service(remotive, arbeitnow);

        service.sync();
        JobSyncReport retry = service.sync();

        assertThat(retry.sources().get(0).status()).isEqualTo(SourceSyncStatus.SYNCED);
    }

    // ---------- Consultas ----------

    @Test
    void statsListConfiguredSourcesEvenWithoutPostings() {
        syncState.saveLastSync("Arbeitnow", NOW.minus(Duration.ofHours(2)));
        syncState.saveLastSync("Remotive", NOW.minus(Duration.ofHours(1)));
        postings.saveAll(postings("Arbeitnow", 3, "Java"));

        JobStats stats = service(new FakeSource("Remotive", List::of), new FakeSource("Arbeitnow", List::of))
                .getStats();

        assertThat(stats.totalPostings()).isEqualTo(3);
        assertThat(stats.sources()).containsExactly(
                new JobStats.SourceStats("Remotive", 0, NOW.minus(Duration.ofHours(1))),
                new JobStats.SourceStats("Arbeitnow", 3, NOW.minus(Duration.ofHours(2))));
        assertThat(stats.lastSyncAt()).isEqualTo(NOW.minus(Duration.ofHours(1)));
    }

    @Test
    void statsWithoutAnySyncHaveNullDate() {
        assertThat(service(new FakeSource("Remotive", List::of)).getStats().lastSyncAt()).isNull();
    }

    @Test
    void rejectsListLimitOutOfRange() {
        assertThatThrownBy(() -> service().getNewestJobs(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().getNewestJobs(JobServiceImpl.MAX_LIST_LIMIT + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- Fakes ----------

    /** n ofertas de la fuente; la i-ésima se publicó i horas antes de NOW. */
    private static List<JobPosting> postings(String source, int n, String text) {
        return IntStream.range(0, n)
                .mapToObj(i -> posting(source, source + "-" + i, text, NOW.minus(Duration.ofHours(i))))
                .toList();
    }

    private static JobPosting posting(String source, String externalId, String text, Instant publishedAt) {
        return new JobPosting(null, source, externalId, "Developer", "ACME", "Remote", true, null,
                "https://example.com/" + externalId, text, publishedAt, NOW);
    }

    private static final class FakeSource implements JobPostingSource {
        private final String name;
        private final Supplier<List<JobPosting>> fetcher;
        int calls;

        FakeSource(String name, Supplier<List<JobPosting>> fetcher) {
            this.name = name;
            this.fetcher = fetcher;
        }

        @Override public String name() { return name; }

        @Override
        public List<JobPosting> fetch() {
            calls++;
            return fetcher.get();
        }
    }

    private static final class InMemoryPostings implements JobPostingRepository {
        private final List<JobPosting> stored = new ArrayList<>();
        private long nextId = 1;

        @Override
        public Set<String> findExternalIdsBySource(String source) {
            return stored.stream().filter(p -> p.source().equals(source))
                    .map(JobPosting::externalId).collect(Collectors.toSet());
        }

        @Override
        public void saveAll(Collection<JobPosting> postings) {
            postings.forEach(p -> stored.add(new JobPosting(nextId++, p.source(), p.externalId(), p.title(),
                    p.company(), p.location(), p.remote(), p.tags(), p.url(), p.description(),
                    p.publishedAt(), p.fetchedAt())));
        }

        @Override public List<JobPosting> findAll() { return List.copyOf(stored); }

        @Override
        public List<JobPosting> findNewest(int limit) {
            return stored.stream().sorted(newestFirst()).limit(limit).toList();
        }

        @Override public long count() { return stored.size(); }

        @Override
        public Map<String, Long> countBySource() {
            return stored.stream().collect(Collectors.groupingBy(JobPosting::source, LinkedHashMap::new,
                    Collectors.counting()));
        }

        @Override
        public int deletePublishedBefore(Instant cutoff) {
            int before = stored.size();
            stored.removeIf(p -> p.publishedAt().isBefore(cutoff));
            return before - stored.size();
        }

        @Override
        public int deleteAllButNewest(int max) {
            List<JobPosting> keep = stored.stream().sorted(newestFirst()).limit(max).toList();
            int removed = stored.size() - keep.size();
            stored.retainAll(keep);
            return removed;
        }

        private static Comparator<JobPosting> newestFirst() {
            return Comparator.comparing(JobPosting::publishedAt).reversed();
        }
    }

    private static final class InMemorySyncState implements JobSyncStateRepository {
        private final Map<String, Instant> syncs = new LinkedHashMap<>();

        @Override public Optional<Instant> findLastSync(String source) { return Optional.ofNullable(syncs.get(source)); }
        @Override public Map<String, Instant> findAll() { return Map.copyOf(syncs); }
        @Override public void saveLastSync(String source, Instant syncedAt) { syncs.put(source, syncedAt); }
    }

    private static final class InMemorySkills implements SkillRepository {
        private final Map<Long, Skill> skills = new HashMap<>();

        InMemorySkills(Skill... initial) {
            for (Skill skill : initial) {
                skills.put(skill.getId(), skill);
            }
        }

        Skill byName(String name) {
            return skills.values().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
        }

        @Override public List<Skill> findAll() { return List.copyOf(skills.values()); }
        @Override public Optional<Skill> findById(Long id) { return Optional.ofNullable(skills.get(id)); }

        @Override
        public Skill save(Skill skill) {
            skills.put(skill.getId(), skill);
            return skill;
        }
    }
}
