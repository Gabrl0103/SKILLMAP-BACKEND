package com.skillmap.api.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.skillmap.api.application.service.JobService;
import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.model.JobStats;
import com.skillmap.api.domain.model.JobSyncReport;
import com.skillmap.api.domain.repository.JobPostingRepository;
import com.skillmap.api.domain.repository.JobSyncStateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Solo lee JSON local: nunca llama a Remotive, Arbeitnow ni a ninguna otra API. */
class JobPostingSeederTest {

    private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final String TWO_POSTINGS = """
            [
              {"source": "Remotive", "externalId": "1", "title": "Java Developer", "company": "Acme",
               "location": "Worldwide", "remote": true, "tags": "java,spring",
               "url": "https://remotive.com/remote-jobs/software-dev/java-developer-1",
               "description": "Spring Boot y SQL", "publishedAt": "2026-10-01T10:00:00Z",
               "fetchedAt": "2026-10-03T02:16:22Z"},
              {"source": "Arbeitnow", "externalId": "a-2", "title": "Frontend Engineer", "company": "Beta",
               "location": "Berlin", "remote": false, "tags": "react",
               "url": "https://www.arbeitnow.com/jobs/a-2", "description": "React y TypeScript",
               "publishedAt": "2026-10-02T10:00:00Z", "fetchedAt": "2026-10-03T01:00:00Z"}
            ]
            """;

    private final InMemoryPostings postings = new InMemoryPostings();
    private final Map<String, Instant> syncs = new LinkedHashMap<>();
    private final FakeJobService jobService = new FakeJobService();

    private JobPostingSeeder seeder(Resource seed) {
        return new JobPostingSeeder(postings, new InMemorySyncState(), jobService, JSON, seed);
    }

    private static Resource json(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importsPostingsWhenTableIsEmptyAndRecalculatesDemand() {
        seeder(json(TWO_POSTINGS)).run();

        assertThat(postings.stored).hasSize(2);
        JobPosting remotive = postings.stored.get(0);
        assertThat(remotive.source()).isEqualTo("Remotive");
        assertThat(remotive.title()).isEqualTo("Java Developer");
        assertThat(remotive.company()).isEqualTo("Acme");
        assertThat(remotive.url()).isEqualTo("https://remotive.com/remote-jobs/software-dev/java-developer-1");
        assertThat(remotive.publishedAt()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        assertThat(remotive.searchableText()).contains("Spring Boot");
        assertThat(jobService.recalculations).isEqualTo(1);
        // La sincronización automática cuenta desde la fecha en que se trajeron las ofertas.
        assertThat(syncs).containsEntry("Remotive", Instant.parse("2026-10-03T02:16:22Z"))
                .containsEntry("Arbeitnow", Instant.parse("2026-10-03T01:00:00Z"));
    }

    @Test
    void doesNothingWhenThereArePostingsAlready() {
        seeder(json(TWO_POSTINGS)).run();

        seeder(json(TWO_POSTINGS)).run();

        assertThat(postings.stored).hasSize(2);
        assertThat(jobService.recalculations).isEqualTo(1);
    }

    @Test
    void invalidJsonDoesNotBreakStartupAndImportsNothing() {
        JobPostingSeeder seeder = seeder(json("[{\"source\": \"Remotive\", "));

        assertThatCode(seeder::run).doesNotThrowAnyException();

        assertThat(postings.stored).isEmpty();
        assertThat(jobService.recalculations).isZero();
        assertThat(syncs).isEmpty();
    }

    @Test
    void invalidJsonFailsWithClearMessage() {
        assertThatThrownBy(() -> seeder(json("{ no es json")).read())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("seed/job-postings.json")
                .hasMessageContaining("no es un JSON válido de ofertas");
    }

    @Test
    void postingWithoutRequiredFieldsFailsWithClearMessage() {
        String missingSource = "[{\"externalId\": \"1\", \"publishedAt\": \"2026-10-01T10:00:00Z\"}]";

        assertThatThrownBy(() -> seeder(json(missingSource)).read())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("la oferta 0 no tiene source, externalId o publishedAt");
        assertThatCode(() -> seeder(json(missingSource)).run()).doesNotThrowAnyException();
        assertThat(postings.stored).isEmpty();
    }

    @Test
    void seedIncludedInTheAppIsValid() {
        List<JobPosting> included = seeder(new ClassPathResource(JobPostingSeeder.RESOURCE)).read();

        assertThat(included).isNotEmpty();
        assertThat(included).allSatisfy(p -> {
            assertThat(p.source()).isIn("Remotive", "Arbeitnow");
            assertThat(p.title()).isNotBlank();
            assertThat(p.url()).startsWith("https://");
            assertThat(p.publishedAt()).isNotNull();
        });
    }

    private final class InMemorySyncState implements JobSyncStateRepository {
        @Override public Optional<Instant> findLastSync(String source) { return Optional.ofNullable(syncs.get(source)); }
        @Override public Map<String, Instant> findAll() { return Map.copyOf(syncs); }
        @Override public void saveLastSync(String source, Instant syncedAt) { syncs.put(source, syncedAt); }
    }

    private static final class InMemoryPostings implements JobPostingRepository {
        private final List<JobPosting> stored = new ArrayList<>();

        @Override public Set<String> findExternalIdsBySource(String source) { return Set.of(); }
        @Override public void saveAll(Collection<JobPosting> postings) { stored.addAll(postings); }
        @Override public List<JobPosting> findAll() { return List.copyOf(stored); }
        @Override public Optional<JobPosting> findById(Long id) { return stored.stream().filter(p -> id.equals(p.id())).findFirst(); }
        @Override public List<JobPosting> findNewest(int limit) { return List.of(); }
        @Override public long count() { return stored.size(); }
        @Override public Map<String, Long> countBySource() { return Map.of(); }
        @Override public int deletePublishedBefore(Instant cutoff) { return 0; }
        @Override public int deleteAllButNewest(int max) { return 0; }
    }

    /** Ninguna fuente real: sincronizar aquí sería un error del seeder. */
    private static final class FakeJobService implements JobService {
        private int recalculations;

        @Override public JobSyncReport sync() { throw new AssertionError("El seeder no debe sincronizar"); }
        @Override public JobSyncReport syncSource(String source) { throw new AssertionError("El seeder no debe sincronizar"); }
        @Override public List<JobPosting> getNewestJobs(int limit) { return List.of(); }
        @Override public JobPosting getJob(Long id) { throw new UnsupportedOperationException(); }
        @Override public JobStats getStats() { return new JobStats(0, List.of(), null); }

        @Override
        public boolean recalculateDemand() {
            recalculations++;
            return true;
        }
    }
}
