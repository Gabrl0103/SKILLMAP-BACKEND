package com.skillmap.api.infrastructure.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillmap.api.domain.exception.JobSourceException;
import com.skillmap.api.domain.exception.JobSourceInvalidResponseException;
import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.port.JobPostingSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Adaptador de Arbeitnow (https://www.arbeitnow.com/blog/job-board-api).
 * Pagina con ?page=N y se detiene al llegar al máximo configurado o cuando no hay "links.next".
 * Es una bolsa generalista: solo se devuelven las ofertas técnicas (ver TechJobFilter), para que
 * no se guarden ni cuenten en el porcentaje de demanda.
 *
 * Campos usados de cada oferta: slug, company_name, title, description (HTML escapado),
 * remote, url, tags, location y created_at (segundos Unix).
 */
@Component
@Order(2)
public class ArbeitnowJobSource implements JobPostingSource {

    private static final Logger log = LoggerFactory.getLogger(ArbeitnowJobSource.class);
    static final String NAME = "Arbeitnow";

    private final JobSourceHttpClient http;
    private final String baseUrl;
    private final int maxPages;
    private final TechJobFilter techFilter;
    private final Clock clock;

    public ArbeitnowJobSource(JobSourceHttpClient http,
                              @Value("${jobs.arbeitnow.base-url}") String baseUrl,
                              @Value("${jobs.arbeitnow.max-pages}") int maxPages,
                              @Value("${jobs.arbeitnow.tech-keywords}") List<String> techKeywords,
                              @Value("${jobs.arbeitnow.tech-title-keywords}") List<String> techTitleKeywords,
                              @Value("${jobs.arbeitnow.excluded-title-keywords}") List<String> excludedTitleKeywords,
                              Clock clock) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.maxPages = maxPages;
        this.techFilter = new TechJobFilter(techKeywords, techTitleKeywords, excludedTitleKeywords);
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<JobPosting> fetch() {
        Instant now = clock.instant();
        List<JobPosting> postings = new ArrayList<>();
        int nonTechnical = 0;
        for (int page = 1; page <= maxPages; page++) {
            JsonNode root;
            try {
                root = http.getJson(NAME, UriComponentsBuilder.fromHttpUrl(baseUrl)
                        .path("/job-board-api")
                        .queryParam("page", page)
                        .build().toUri());
            } catch (JobSourceException e) {
                // Si ya hay páginas buenas se aprovechan; si falla la primera, la fuente ha fallado.
                if (page == 1) {
                    throw e;
                }
                log.warn("{}: falló la página {}, se usan las {} anteriores: {}", NAME, page, page - 1, e.getMessage());
                break;
            }
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                throw new JobSourceInvalidResponseException(NAME + " devolvió una respuesta sin la lista de ofertas.");
            }
            for (JsonNode job : data) {
                JobPosting posting = toPosting(job, now);
                if (posting == null) {
                    continue;
                }
                if (techFilter.isTechnical(posting.title(), posting.tags())) {
                    postings.add(posting);
                } else {
                    nonTechnical++;
                }
            }
            if (data.isEmpty() || JobJson.text(root.path("links"), "next") == null) {
                break;
            }
        }
        log.info("{}: {} ofertas técnicas, {} descartadas por no serlo", NAME, postings.size(), nonTechnical);
        return postings;
    }

    private static JobPosting toPosting(JsonNode job, Instant now) {
        String slug = JobJson.text(job, "slug");
        String title = JobJson.text(job, "title");
        if (slug == null || title == null) {
            return null;
        }
        JsonNode createdAt = job.get("created_at");
        Instant publishedAt = createdAt != null && createdAt.canConvertToLong()
                ? Instant.ofEpochSecond(createdAt.asLong())
                : now;
        return new JobPosting(
                null,
                NAME,
                slug,
                title,
                JobJson.text(job, "company_name"),
                JobJson.text(job, "location"),
                job.path("remote").asBoolean(false),
                JobJson.tags(job, "tags"),
                JobJson.text(job, "url"),
                JobJson.plainText(JobJson.text(job, "description")),
                publishedAt,
                now);
    }
}
