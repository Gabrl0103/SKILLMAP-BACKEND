package com.skillmap.api.infrastructure.jobs;

import com.skillmap.api.domain.exception.JobSourceInvalidResponseException;
import com.skillmap.api.domain.exception.JobSourceRateLimitException;
import com.skillmap.api.domain.model.JobPosting;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArbeitnowJobSourceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    private static final List<String> KEYWORDS = List.of("software", "developer", "qa", "entwickler");
    private static final List<String> TITLE_KEYWORDS = List.of("engineer", "data");
    private static final List<String> EXCLUDED = List.of("mechanical", "electrical", "civil", "hausmeister",
            "haustechniker", "data protection", "datenschutz");

    private static ArbeitnowJobSource source(FakeJobServer server) {
        return source(server, KEYWORDS, TITLE_KEYWORDS, EXCLUDED);
    }

    private static ArbeitnowJobSource source(FakeJobServer server, List<String> keywords,
                                             List<String> titleKeywords, List<String> excluded) {
        return new ArbeitnowJobSource(new JobSourceHttpClient(RestClient.builder(), Duration.ofSeconds(5)),
                server.baseUrl(), 3, keywords, titleKeywords, excluded, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static int page(URI uri) {
        return Integer.parseInt(uri.getQuery().replace("page=", ""));
    }

    /** Forma real de la respuesta (campos verificados contra la API pública). */
    private static String pageBody(int page, boolean hasNext) {
        String next = hasNext ? "\"https://www.arbeitnow.com/api/job-board-api?page=" + (page + 1) + "\"" : "null";
        return """
                {"data": [
                  {"slug": "backend-dev-%1$d", "company_name": "jetbrains", "title": "Backend Developer",
                   "description": "&lt;p&gt;Kotlin &amp;amp; Java&lt;/p&gt;&lt;p&gt;K8s&lt;/p&gt;",
                   "remote": false, "url": "https://www.arbeitnow.com/jobs/x-%1$d",
                   "tags": ["Software Development", "IT"], "job_types": [], "location": "Berlin; Munich",
                   "created_at": 1790985313}
                 ],
                 "links": {"first": "x", "last": null, "prev": null, "next": %2$s},
                 "meta": {"current_page": %1$d}}
                """.formatted(page, next);
    }

    @Test
    void readsAtMostThreePages() throws IOException {
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json(pageBody(page(uri), true)))) {
            List<JobPosting> postings = source(server).fetch();

            assertThat(server.requests).extracting(URI::getQuery)
                    .containsExactly("page=1", "page=2", "page=3");
            assertThat(postings).extracting(JobPosting::externalId)
                    .containsExactly("backend-dev-1", "backend-dev-2", "backend-dev-3");
        }
    }

    @Test
    void stopsWhenThereIsNoNextPage() throws IOException {
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json(pageBody(page(uri), false)))) {
            assertThat(source(server).fetch()).hasSize(1);
            assertThat(server.requests).hasSize(1);
        }
    }

    @Test
    void stopsOnEmptyPage() throws IOException {
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json(page(uri) == 1
                ? pageBody(1, true)
                : "{\"data\": [], \"links\": {\"next\": \"x\"}}"))) {
            assertThat(source(server).fetch()).hasSize(1);
            assertThat(server.requests).hasSize(2);
        }
    }

    @Test
    void parsesEscapedHtmlAndUnixTimestamp() throws IOException {
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json(pageBody(1, false)))) {
            JobPosting posting = source(server).fetch().get(0);

            assertThat(posting.source()).isEqualTo("Arbeitnow");
            assertThat(posting.title()).isEqualTo("Backend Developer");
            assertThat(posting.company()).isEqualTo("jetbrains");
            assertThat(posting.location()).isEqualTo("Berlin; Munich");
            assertThat(posting.remote()).isFalse();
            assertThat(posting.tags()).isEqualTo("Software Development, IT");
            assertThat(posting.url()).isEqualTo("https://www.arbeitnow.com/jobs/x-1");
            assertThat(posting.description()).isEqualTo("Kotlin & Java K8s");
            assertThat(posting.publishedAt()).isEqualTo(Instant.ofEpochSecond(1790985313));
            assertThat(posting.fetchedAt()).isEqualTo(NOW);
        }
    }

    @Test
    void laterPageFailureKeepsEarlierPages() throws IOException {
        try (var server = new FakeJobServer(uri -> page(uri) == 1
                ? FakeJobServer.Response.json(pageBody(1, true))
                : FakeJobServer.Response.status(429))) {
            assertThat(source(server).fetch()).extracting(JobPosting::externalId).containsExactly("backend-dev-1");
        }
    }

    @Test
    void firstPageFailureFailsTheSource() throws IOException {
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.status(429))) {
            assertThatThrownBy(() -> source(server).fetch())
                    .isInstanceOf(JobSourceRateLimitException.class)
                    .hasMessageContaining("Arbeitnow alcanzó su límite de uso");
        }
    }

    // ---------- Filtro de ofertas técnicas ----------

    private static String job(String slug, String title, String tagsJson) {
        return """
                {"slug": "%s", "company_name": "c", "title": "%s", "description": "d", "remote": true,
                 "url": "u", "tags": %s, "location": "Berlin", "created_at": 1790985313}
                """.formatted(slug, title, tagsJson);
    }

    private static String singlePage(String... jobs) {
        return "{\"data\": [" + String.join(",", jobs) + "], \"links\": {\"next\": null}}";
    }

    @Test
    void keepsOnlyTechnicalPostingsByTitleOrTags() throws IOException {
        String body = singlePage(
                job("dev", "Senior Software Engineer", "[]"),
                job("de", "Softwareentwickler (m/w/d)", "[]"),
                job("by-tag", "Werkstudent", "[\"Software Development\"]"),
                job("qa", "QA Lead", "[]"),
                job("data", "Senior Data Analyst", "[]"),
                job("pm", "Senior Product Manager", "[\"Business Applications Development\"]"),
                job("sales", "Vertriebsmitarbeiter", "[\"Sales\"]"),
                job("qatar", "Office Manager Qatar", "[]"));
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json(body))) {
            assertThat(source(server).fetch()).extracting(JobPosting::externalId)
                    .containsExactly("dev", "de", "by-tag", "qa", "data");
        }
    }

    @Test
    void engineerAndDataCountOnlyInTheTitle() throws IOException {
        String body = singlePage(
                job("eng-title", "Platform Engineer", "[]"),
                job("eng-tag", "Technischer Anlagenbetreuer", "[\"Building, Supply, Safety Services Engineering\"]"),
                job("data-tag", "AI Resident", "[\"Data\"]"),
                job("support", "Technical Support Specialist", "[\"Engineering\"]"));
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json(body))) {
            assertThat(source(server).fetch()).extracting(JobPosting::externalId).containsExactly("eng-title");
        }
    }

    @Test
    void excludedTitlesAreDroppedEvenWithTechKeywords() throws IOException {
        String body = singlePage(
                job("mech", "Mechanical Engineer | Robotics", "[\"Engineering\"]"),
                job("elec", "Electrical Engineer (m/w/d)", "[]"),
                job("civil", "Civil Engineer", "[]"),
                job("hm", "Hausmeister (m/w/d) Potsdam", "[\"Software Development\"]"),
                job("ht", "Haustechniker Gebäudemanagement (m/w/d)", "[]"),
                job("dp", "Data Protection Specialist (m/w/d)", "[\"Data\"]"),
                job("ds", "Datenschutzbeauftragter Softwareentwicklung", "[]"),
                job("ok", "Data Engineer", "[]"));
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json(body))) {
            assertThat(source(server).fetch()).extracting(JobPosting::externalId).containsExactly("ok");
        }
    }

    @Test
    void exclusionsOnlyLookAtTheTitle() throws IOException {
        String body = singlePage(job("sw", "Embedded Software Developer", "[\"Electrical\", \"Mechanical\"]"));
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json(body))) {
            assertThat(source(server).fetch()).hasSize(1);
        }
    }

    @Test
    void keywordMatchesWordStartOnly() throws IOException {
        // "data" no debe encontrarse dentro de "Mandate"; "engineer" sí al inicio de "Engineering".
        String body = singlePage(
                job("eng", "Head of Engineering", "[]"),
                job("mandate", "Mandate Manager", "[]"));
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json(body))) {
            assertThat(source(server).fetch()).extracting(JobPosting::externalId).containsExactly("eng");
        }
    }

    @Test
    void emptyKeywordListsDisableTheFilterButKeepExclusions() throws IOException {
        String body = singlePage(
                job("pm", "Senior Product Manager", "[]"),
                job("hm", "Hausmeister", "[]"));
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json(body))) {
            assertThat(source(server, List.of(), List.of(), EXCLUDED).fetch())
                    .extracting(JobPosting::externalId).containsExactly("pm");
        }
    }

    @Test
    void missingDataListIsInvalidResponse() throws IOException {
        try (var server = new FakeJobServer(uri -> FakeJobServer.Response.json("{\"message\": \"x\"}"))) {
            assertThatThrownBy(() -> source(server).fetch())
                    .isInstanceOf(JobSourceInvalidResponseException.class)
                    .hasMessage("Arbeitnow devolvió una respuesta sin la lista de ofertas.");
        }
    }
}
