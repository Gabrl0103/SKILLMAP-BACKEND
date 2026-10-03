package com.skillmap.api.infrastructure.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillmap.api.domain.exception.JobSourceInvalidResponseException;
import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.port.JobPostingSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adaptador de Remotive (https://remotive.com/api-documentation), solo ofertas de categorías técnicas.
 * Remotive pide no consultar su API más de unas 4 veces al día: de eso se encarga JobServiceImpl.
 * El feed público devuelve pocas ofertas por consulta; el volumen sale de acumularlas día a día.
 *
 * Campos usados de cada oferta: id, url, title, company_name, category, tags,
 * publication_date, candidate_required_location y description (HTML).
 */
@Component
@Order(1)
public class RemotiveJobSource implements JobPostingSource {

    private static final Logger log = LoggerFactory.getLogger(RemotiveJobSource.class);
    static final String NAME = "Remotive";
    /** Slug según GET /api/remote-jobs/categories. Se envía por si el feed vuelve a respetarlo. */
    static final String CATEGORY_SLUG = "software-development";

    private final JobSourceHttpClient http;
    private final URI uri;
    /**
     * Nombres de categoría aceptados, en minúsculas. El feed público ignora el filtro de la URL y
     * mezcla categorías, así que se filtra aquí. Vacío = se aceptan todas.
     */
    private final Set<String> acceptedCategories;
    private final Clock clock;

    public RemotiveJobSource(JobSourceHttpClient http,
                             @Value("${jobs.remotive.base-url}") String baseUrl,
                             @Value("${jobs.remotive.categories}") List<String> acceptedCategories,
                             Clock clock) {
        this.http = http;
        this.uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/remote-jobs")
                .queryParam("category", CATEGORY_SLUG)
                .build().toUri();
        this.acceptedCategories = acceptedCategories.stream()
                .map(c -> c.strip().toLowerCase(Locale.ROOT))
                .filter(c -> !c.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<JobPosting> fetch() {
        JsonNode jobs = http.getJson(NAME, uri).get("jobs");
        if (jobs == null || !jobs.isArray()) {
            throw new JobSourceInvalidResponseException(NAME + " devolvió una respuesta sin la lista de ofertas.");
        }
        Instant now = clock.instant();
        List<JobPosting> postings = new ArrayList<>();
        int otherCategory = 0;
        int incomplete = 0;
        for (JsonNode job : jobs) {
            if (!isAccepted(JobJson.text(job, "category"))) {
                otherCategory++;
                continue;
            }
            String id = JobJson.text(job, "id");
            String title = JobJson.text(job, "title");
            if (id == null || title == null) {
                incomplete++;
                continue;
            }
            postings.add(new JobPosting(
                    null,
                    NAME,
                    id,
                    title,
                    JobJson.text(job, "company_name"),
                    JobJson.text(job, "candidate_required_location"),
                    true,
                    JobJson.tags(job, "tags"),
                    JobJson.text(job, "url"),
                    JobJson.plainText(JobJson.text(job, "description")),
                    parseDate(JobJson.text(job, "publication_date"), now),
                    now));
        }
        if (otherCategory + incomplete > 0) {
            log.info("{}: se descartan {} ofertas de otra categoría y {} incompletas",
                    NAME, otherCategory, incomplete);
        }
        return postings;
    }

    private boolean isAccepted(String category) {
        if (acceptedCategories.isEmpty()) {
            return true;
        }
        return category != null && acceptedCategories.contains(category.toLowerCase(Locale.ROOT));
    }

    /** Remotive manda "2026-09-30T13:15:26", sin zona: se interpreta en UTC. */
    static Instant parseDate(String value, Instant fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ignored) {
                return fallback;
            }
        }
    }
}
