package com.skillmap.api.infrastructure.jobs;

import com.skillmap.api.domain.exception.JobSourceException;
import com.skillmap.api.domain.exception.JobSourceInvalidResponseException;
import com.skillmap.api.domain.exception.JobSourceRateLimitException;
import com.skillmap.api.domain.exception.JobSourceUnavailableException;
import com.skillmap.api.domain.model.JobPosting;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemotiveJobSourceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    /** Forma real de la respuesta (campos verificados contra la API pública). */
    private static final String JOBS = """
            {"00-warning": "...", "0-legal-notice": "...", "job-count": 3, "total-job-count": 3,
             "jobs": [
              {"id": 2091132, "url": "https://remotive.com/remote-jobs/software-development/x-2091132",
               "title": "Senior Back-end Engineer", "company_name": "Lemon.io", "company_logo": "",
               "category": "Software Development", "tags": ["java", "spring", " "], "job_type": "full_time",
               "publication_date": "2026-09-30T13:15:26", "candidate_required_location": "Europe, USA",
               "salary": "", "description": "<p><strong>Java</strong> &amp; Spring</p><ul><li>Docker</li></ul>"},
              {"id": 2091133, "url": "u", "title": "Growth Marketer", "company_name": "ACME",
               "category": "Marketing", "tags": [], "publication_date": "2026-09-30T10:00:00",
               "candidate_required_location": "Worldwide", "description": "<p>SEO</p>"},
              {"id": null, "title": "Sin id", "category": "Software Development"}
             ]}
            """;

    /** Igual que jobs.remotive.categories en application.properties. */
    private static final List<String> CATEGORIES = List.of("Software Development", "Artificial Intelligence",
            "Data and Analytics", "Devops", "Quality Assurance", "Information Technology");

    private static RemotiveJobSource source(FakeJobServer server, Duration timeout) {
        return source(server.baseUrl(), timeout, CATEGORIES);
    }

    private static RemotiveJobSource source(String baseUrl, Duration timeout, List<String> categories) {
        return new RemotiveJobSource(new JobSourceHttpClient(RestClient.builder(), timeout),
                baseUrl, categories, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RemotiveJobSource source(FakeJobServer server) {
        return source(server, Duration.ofSeconds(5));
    }

    @Test
    void requestsSoftwareDevelopmentCategoryAndParsesFields() throws IOException {
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json(JOBS))) {
            List<JobPosting> postings = source(server).fetch();

            assertThat(server.requests).hasSize(1);
            assertThat(server.requests.get(0).getPath()).isEqualTo("/api/remote-jobs");
            assertThat(server.requests.get(0).getQuery()).isEqualTo("category=software-development");

            // Se descartan la de Marketing (el feed no siempre filtra) y la que no tiene id.
            assertThat(postings).hasSize(1);
            JobPosting posting = postings.get(0);
            assertThat(posting.source()).isEqualTo("Remotive");
            assertThat(posting.externalId()).isEqualTo("2091132");
            assertThat(posting.title()).isEqualTo("Senior Back-end Engineer");
            assertThat(posting.company()).isEqualTo("Lemon.io");
            assertThat(posting.location()).isEqualTo("Europe, USA");
            assertThat(posting.remote()).isTrue();
            assertThat(posting.tags()).isEqualTo("java, spring");
            assertThat(posting.description()).isEqualTo("Java & Spring Docker");
            assertThat(posting.publishedAt()).isEqualTo(Instant.parse("2026-09-30T13:15:26Z"));
            assertThat(posting.fetchedAt()).isEqualTo(NOW);
        }
    }

    @Test
    void rateLimitIsReportedClearly() throws IOException {
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.status(429))) {
            assertThatThrownBy(() -> source(server).fetch())
                    .isInstanceOf(JobSourceRateLimitException.class)
                    .hasMessage("Remotive alcanzó su límite de uso. Inténtalo de nuevo en unas horas.");
        }
    }

    @Test
    void serverErrorMeansSourceIsDown() throws IOException {
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.status(503))) {
            assertThatThrownBy(() -> source(server).fetch())
                    .isInstanceOf(JobSourceUnavailableException.class)
                    .hasMessageContaining("Remotive no está disponible")
                    .hasMessageContaining("503");
        }
    }

    @Test
    void otherClientErrorSuggestsApiChange() throws IOException {
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.status(404))) {
            assertThatThrownBy(() -> source(server).fetch())
                    .isExactlyInstanceOf(JobSourceException.class)
                    .hasMessageContaining("HTTP 404");
        }
    }

    @Test
    void htmlInsteadOfJsonIsInvalidResponse() throws IOException {
        try (var server = new FakeJobServer(uri ->
                new FakeJobServer.Response(200, "text/html", "<html>Mantenimiento</html>", 0))) {
            assertThatThrownBy(() -> source(server).fetch())
                    .isInstanceOf(JobSourceInvalidResponseException.class)
                    .hasMessage("Remotive devolvió una respuesta que no es JSON válido.");
        }
    }

    @Test
    void brokenJsonIsInvalidResponse() throws IOException {
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json("{\"jobs\": [ {"))) {
            assertThatThrownBy(() -> source(server).fetch())
                    .isInstanceOf(JobSourceInvalidResponseException.class);
        }
    }

    @Test
    void missingJobsListIsInvalidResponse() throws IOException {
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json("{\"error\": \"x\"}"))) {
            assertThatThrownBy(() -> source(server).fetch())
                    .isInstanceOf(JobSourceInvalidResponseException.class)
                    .hasMessage("Remotive devolvió una respuesta sin la lista de ofertas.");
        }
    }

    @Test
    void slowResponseIsReportedAsUnavailable() throws IOException {
        try (var server = new FakeJobServer(uri ->
                new FakeJobServer.Response(200, "application/json", JOBS, 1_000))) {
            assertThatThrownBy(() -> source(server, Duration.ofMillis(200)).fetch())
                    .isInstanceOf(JobSourceUnavailableException.class)
                    .hasMessageContaining("tardó demasiado");
        }
    }

    @Test
    void unreachableHostIsReportedAsUnavailable() throws IOException {
        String baseUrl;
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json(JOBS))) {
            baseUrl = server.baseUrl();
        }
        var source = source(baseUrl, Duration.ofSeconds(2), CATEGORIES);

        assertThatThrownBy(source::fetch)
                .isInstanceOf(JobSourceUnavailableException.class)
                .hasMessageContaining("No se pudo conectar con Remotive");
    }

    // ---------- Filtro de categorías ----------

    /** Una oferta por categoría, con los nombres reales de GET /api/remote-jobs/categories. */
    private static final String MIXED_CATEGORIES = """
            {"job-count": 10, "jobs": [
              {"id": 1, "title": "Backend", "category": "Software Development"},
              {"id": 2, "title": "AI", "category": "Artificial Intelligence"},
              {"id": 3, "title": "Analyst", "category": "Data and Analytics"},
              {"id": 4, "title": "SRE", "category": "Devops"},
              {"id": 5, "title": "QA", "category": "Quality Assurance"},
              {"id": 6, "title": "Sysadmin", "category": "Information Technology"},
              {"id": 7, "title": "Copywriter", "category": "Writing"},
              {"id": 8, "title": "Designer", "category": "Design"},
              {"id": 9, "title": "Support", "category": "Customer Service"},
              {"id": 10, "title": "Sin categoría"}
            ]}
            """;

    @Test
    void keepsOnlyConfiguredTechnicalCategories() throws IOException {
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json(MIXED_CATEGORIES))) {
            assertThat(source(server).fetch()).extracting(JobPosting::externalId)
                    .containsExactly("1", "2", "3", "4", "5", "6");
            // El slug de la URL no cambia: solo se filtra al recibir.
            assertThat(server.requests.get(0).getQuery()).isEqualTo("category=software-development");
        }
    }

    @Test
    void categoryMatchIgnoresCaseAndSpaces() throws IOException {
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json(MIXED_CATEGORIES))) {
            var source = source(server.baseUrl(), Duration.ofSeconds(5), List.of(" DevOps ", "quality assurance"));
            assertThat(source.fetch()).extracting(JobPosting::externalId).containsExactly("4", "5");
        }
    }

    @Test
    void emptyCategoryListAcceptsEverything() throws IOException {
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json(MIXED_CATEGORIES))) {
            var source = source(server.baseUrl(), Duration.ofSeconds(5), List.of());
            assertThat(source.fetch()).hasSize(10);
        }
    }

    @Test
    void unparseableDateFallsBackToNow() {
        assertThat(RemotiveJobSource.parseDate("ayer", NOW)).isEqualTo(NOW);
        assertThat(RemotiveJobSource.parseDate(null, NOW)).isEqualTo(NOW);
        assertThat(RemotiveJobSource.parseDate("2026-09-30T13:15:26+02:00", NOW))
                .isEqualTo(Instant.parse("2026-09-30T11:15:26Z"));
    }
}
