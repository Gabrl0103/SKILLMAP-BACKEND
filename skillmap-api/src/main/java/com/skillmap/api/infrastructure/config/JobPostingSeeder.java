package com.skillmap.api.infrastructure.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillmap.api.application.service.JobService;
import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.repository.JobPostingRepository;
import com.skillmap.api.domain.repository.JobSyncStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

/**
 * Ofertas incluidas en la app (seed/job-postings.json, exportadas con scripts/ExportJobPostings.java):
 * con la tabla de ofertas vacía se importan sin llamar a ninguna API y se recalcula la demanda,
 * así la app no necesita sincronizar en el primer arranque. Con ofertas guardadas no hace nada.
 * Va después de DataSeeder (la demanda se calcula sobre sus habilidades). Solo en el perfil de
 * escritorio (el perfil cloud no escanea componentes). Un JSON inválido no tumba el arranque.
 */
@Component
@Order(2)
public class JobPostingSeeder implements CommandLineRunner {

    static final String RESOURCE = "seed/job-postings.json";
    private static final Logger log = LoggerFactory.getLogger(JobPostingSeeder.class);

    private final JobPostingRepository postingRepository;
    private final JobSyncStateRepository syncStateRepository;
    private final JobService jobService;
    private final ObjectMapper objectMapper;
    private final Resource seed;

    @Autowired
    public JobPostingSeeder(JobPostingRepository postingRepository, JobSyncStateRepository syncStateRepository,
                            JobService jobService, ObjectMapper objectMapper) {
        this(postingRepository, syncStateRepository, jobService, objectMapper, new ClassPathResource(RESOURCE));
    }

    /** Los tests pasan su propio JSON. */
    JobPostingSeeder(JobPostingRepository postingRepository, JobSyncStateRepository syncStateRepository,
                     JobService jobService, ObjectMapper objectMapper, Resource seed) {
        this.postingRepository = postingRepository;
        this.syncStateRepository = syncStateRepository;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
        this.seed = seed;
    }

    @Override
    public void run(String... args) {
        if (postingRepository.count() > 0) {
            return;
        }
        try {
            List<JobPosting> postings = read();
            postingRepository.saveAll(postings);
            // Fecha de los datos: la sincronización automática de Remotive cuenta sus días desde aquí.
            postings.stream()
                    .filter(p -> p.fetchedAt() != null)
                    .collect(Collectors.toMap(JobPosting::source, JobPosting::fetchedAt,
                            BinaryOperator.maxBy(Instant::compareTo)))
                    .forEach(syncStateRepository::saveLastSync);
            boolean recalculated = jobService.recalculateDemand();
            log.info("{} ofertas importadas de {} (demanda recalculada: {})", postings.size(), RESOURCE, recalculated);
        } catch (RuntimeException e) {
            log.error("No se importaron las ofertas incluidas: {}. La app arranca sin ellas.", e.getMessage());
        }
    }

    /** @throws IllegalStateException con un mensaje claro si el archivo falta o no es válido */
    List<JobPosting> read() {
        List<JobPosting> postings;
        try (InputStream in = seed.getInputStream()) {
            postings = objectMapper.readValue(in, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException(RESOURCE + " falta o no es un JSON válido de ofertas ("
                    + e.getMessage() + ")", e);
        }
        if (postings == null) {
            throw new IllegalStateException(RESOURCE + " no contiene una lista de ofertas");
        }
        for (int i = 0; i < postings.size(); i++) {
            JobPosting p = postings.get(i);
            if (p == null || isBlank(p.source()) || isBlank(p.externalId()) || p.publishedAt() == null) {
                throw new IllegalStateException(RESOURCE + ": la oferta " + i
                        + " no tiene source, externalId o publishedAt");
            }
        }
        return postings;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
