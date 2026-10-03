package com.skillmap.api.application.service.impl;

import com.skillmap.api.application.service.JobService;
import com.skillmap.api.domain.exception.JobSourceException;
import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.model.JobStats;
import com.skillmap.api.domain.model.JobSyncReport;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SourceSyncResult;
import com.skillmap.api.domain.model.SourceSyncStatus;
import com.skillmap.api.domain.port.JobPostingSource;
import com.skillmap.api.domain.repository.JobPostingRepository;
import com.skillmap.api.domain.repository.JobSyncStateRepository;
import com.skillmap.api.domain.repository.SkillRepository;
import com.skillmap.api.domain.service.SkillMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class JobServiceImpl implements JobService {

    private static final Logger log = LoggerFactory.getLogger(JobServiceImpl.class);
    static final int MAX_LIST_LIMIT = 100;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final List<JobPostingSource> sources;
    private final JobPostingRepository postingRepository;
    private final JobSyncStateRepository syncStateRepository;
    private final SkillRepository skillRepository;
    private final Clock clock;
    private final Duration minSyncInterval;
    private final Duration maxAge;
    private final int maxPostings;
    private final int minPostingsForDemand;

    public JobServiceImpl(List<JobPostingSource> sources,
                          JobPostingRepository postingRepository,
                          JobSyncStateRepository syncStateRepository,
                          SkillRepository skillRepository,
                          Clock clock,
                          @Value("${jobs.sync.min-interval}") Duration minSyncInterval,
                          @Value("${jobs.retention.max-age}") Duration maxAge,
                          @Value("${jobs.retention.max-postings}") int maxPostings,
                          @Value("${jobs.demand.min-postings}") int minPostingsForDemand) {
        this.sources = List.copyOf(sources);
        this.postingRepository = postingRepository;
        this.syncStateRepository = syncStateRepository;
        this.skillRepository = skillRepository;
        this.clock = clock;
        this.minSyncInterval = minSyncInterval;
        this.maxAge = maxAge;
        this.maxPostings = maxPostings;
        this.minPostingsForDemand = minPostingsForDemand;
    }

    /** synchronized: la sincronización programada y una manual no pueden pisarse. */
    @Override
    public synchronized JobSyncReport sync() {
        // A segundos: la base de datos no guarda nanosegundos y la fecha debe verse igual antes y después.
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        List<SourceSyncResult> results = new ArrayList<>();
        List<JobSourceException> failures = new ArrayList<>();

        for (JobPostingSource source : sources) {
            Optional<Instant> lastSync = syncStateRepository.findLastSync(source.name());
            if (lastSync.isPresent() && now.isBefore(lastSync.get().plus(minSyncInterval))) {
                results.add(skipped(source.name(), lastSync.get()));
                continue;
            }
            try {
                List<JobPosting> fetched = source.fetch();
                int added = storeNew(source.name(), fetched, now);
                syncStateRepository.saveLastSync(source.name(), now);
                log.info("{}: {} ofertas recibidas, {} nuevas", source.name(), fetched.size(), added);
                results.add(new SourceSyncResult(source.name(), SourceSyncStatus.SYNCED, fetched.size(), added,
                        now, added + " ofertas nuevas de " + fetched.size() + " recibidas."));
            } catch (JobSourceException e) {
                log.warn("No se pudo sincronizar {}: {}", source.name(), e.getMessage(), e.getCause());
                failures.add(e);
                results.add(new SourceSyncResult(source.name(), SourceSyncStatus.FAILED, 0, 0,
                        lastSync.orElse(null), e.getMessage()));
            }
        }
        // Si no funcionó ninguna, no hay nada que resumir: el error de la primera llega al cliente.
        if (!sources.isEmpty() && failures.size() == sources.size()) {
            throw failures.get(0);
        }

        applyRetention(now);
        boolean recalculated = recalculateDemand();
        return new JobSyncReport(results, (int) postingRepository.count(), recalculated);
    }

    @Override
    public List<JobPosting> getNewestJobs(int limit) {
        if (limit < 1 || limit > MAX_LIST_LIMIT) {
            throw new IllegalArgumentException("El límite debe estar entre 1 y " + MAX_LIST_LIMIT + ".");
        }
        return postingRepository.findNewest(limit);
    }

    @Override
    public JobStats getStats() {
        Map<String, Long> counts = postingRepository.countBySource();
        Map<String, Instant> syncs = syncStateRepository.findAll();

        // Las fuentes configuradas siempre aparecen, aunque aún no tengan ofertas.
        Set<String> names = new LinkedHashSet<>();
        sources.forEach(s -> names.add(s.name()));
        names.addAll(counts.keySet());

        List<JobStats.SourceStats> perSource = names.stream()
                .map(name -> new JobStats.SourceStats(name, counts.getOrDefault(name, 0L), syncs.get(name)))
                .toList();
        Instant lastSync = syncs.values().stream().max(Comparator.naturalOrder()).orElse(null);
        return new JobStats(postingRepository.count(), perSource, lastSync);
    }

    private SourceSyncResult skipped(String source, Instant lastSync) {
        String message = source + " ya se sincronizó el " + DATE_FORMAT.format(lastSync)
                + ". Para no superar su límite de uso se sincroniza como mucho cada "
                + minSyncInterval.toHours() + " horas; se podrá de nuevo a partir del "
                + DATE_FORMAT.format(lastSync.plus(minSyncInterval)) + ".";
        return new SourceSyncResult(source, SourceSyncStatus.SKIPPED, 0, 0, lastSync, message);
    }

    /** Guarda solo las que no estaban ya y no son demasiado viejas. @return cuántas se guardaron */
    private int storeNew(String source, List<JobPosting> fetched, Instant now) {
        Set<String> known = postingRepository.findExternalIdsBySource(source);
        Instant cutoff = now.minus(maxAge);
        Map<String, JobPosting> fresh = new LinkedHashMap<>();
        for (JobPosting posting : fetched) {
            if (!known.contains(posting.externalId()) && !posting.publishedAt().isBefore(cutoff)) {
                fresh.putIfAbsent(posting.externalId(), posting);
            }
        }
        postingRepository.saveAll(fresh.values());
        return fresh.size();
    }

    private void applyRetention(Instant now) {
        int expired = postingRepository.deletePublishedBefore(now.minus(maxAge));
        int overflow = postingRepository.deleteAllButNewest(maxPostings);
        if (expired + overflow > 0) {
            log.info("Retención: {} ofertas de más de {} días y {} por encima del máximo de {}",
                    expired, maxAge.toDays(), overflow, maxPostings);
        }
    }

    /** demandPercentage = % de ofertas guardadas que mencionan la habilidad. @return false si no hay muestra suficiente */
    private boolean recalculateDemand() {
        List<String> texts = postingRepository.findAll().stream().map(JobPosting::searchableText).toList();
        if (texts.size() < minPostingsForDemand) {
            log.info("Solo hay {} ofertas (mínimo {}): se mantiene la demanda anterior",
                    texts.size(), minPostingsForDemand);
            return false;
        }
        for (Skill skill : skillRepository.findAll()) {
            SkillMatcher matcher = SkillMatcher.forSkill(skill.getName());
            long mentions = texts.stream().filter(matcher::matches).count();
            skill.updateDemandFromJobPostings((int) Math.round(mentions * 100.0 / texts.size()));
            skillRepository.save(skill);
        }
        return true;
    }
}
