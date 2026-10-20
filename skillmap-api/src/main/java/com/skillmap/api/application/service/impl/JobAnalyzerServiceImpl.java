package com.skillmap.api.application.service.impl;

import com.skillmap.api.application.service.JobAnalyzerService;
import com.skillmap.api.domain.exception.AnalysisHistoryNotFoundException;
import com.skillmap.api.domain.exception.InvalidAnalysisHistoryLimitException;
import com.skillmap.api.domain.model.AnalysisHistory;
import com.skillmap.api.domain.model.AnalysisHistoryDetail;
import com.skillmap.api.domain.model.JobAnalysis;
import com.skillmap.api.domain.model.JobMatch;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.port.JobSkillExtractor;
import com.skillmap.api.domain.repository.AnalysisHistoryRepository;
import com.skillmap.api.domain.service.JobDescriptionText;
import com.skillmap.api.domain.service.JobDescriptionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Ningún log de esta clase incluye el texto de la oferta. */
@Service
public class JobAnalyzerServiceImpl implements JobAnalyzerService {

    static final int MAX_DESCRIPTION_LENGTH = JobDescriptionValidator.MAX_LENGTH;
    static final String CACHE_ENGINE = "cache";
    /** Si el extractor no dice quién es (un doble de test, por ejemplo). */
    private static final String UNKNOWN_ENGINE = "ia";
    private static final double IN_PROGRESS_WEIGHT = 0.5;
    private static final Logger log = LoggerFactory.getLogger(JobAnalyzerServiceImpl.class);

    private final JobSkillExtractor skillExtractor;
    private final UserSkillCatalog userSkills;
    private final AnalysisHistoryRepository historyRepository;
    private final Clock clock;

    public JobAnalyzerServiceImpl(JobSkillExtractor skillExtractor, UserSkillCatalog userSkills,
                                  AnalysisHistoryRepository historyRepository, Clock clock) {
        this.skillExtractor = skillExtractor;
        this.userSkills = userSkills;
        this.historyRepository = historyRepository;
        this.clock = clock;
    }

    @Override
    public JobAnalysis analyze(Long userId, String jobDescription) {
        JobDescriptionValidator.validate(jobDescription);
        String text = jobDescription.strip();
        String textHash = JobDescriptionText.hash(text);
        Map<String, Skill> catalog = catalog(userId);

        // Caché por usuario: la misma oferta ya analizada no vuelve a gastar cuota de IA.
        Optional<AnalysisHistory> previous = historyRepository.findLatestByTextHash(userId, textHash);
        List<String> required;
        String engine;
        if (previous.isPresent()) {
            required = previous.get().detectedSkills();
            engine = CACHE_ENGINE;
        } else {
            String extractorEngine = skillExtractor.engine();
            required = skillExtractor.extractSkills(text, catalog.values().stream().map(Skill::getName).toList());
            engine = extractorEngine == null || extractorEngine.isBlank() ? UNKNOWN_ENGINE : extractorEngine;
        }

        JobMatch match = classify(catalog, required);
        return new JobAnalysis(match, engine, record(userId, text, textHash, engine, match));
    }

    @Override
    public List<AnalysisHistory> getHistory(Long userId, int limit) {
        if (limit < 1 || limit > MAX_HISTORY) {
            throw new InvalidAnalysisHistoryLimitException(MAX_HISTORY);
        }
        return historyRepository.findLatest(userId, limit);
    }

    @Override
    public AnalysisHistoryDetail getHistoryEntry(Long userId, Long id) {
        AnalysisHistory entry = historyRepository.findByIdAndUserId(id, userId)
                .orElseThrow(AnalysisHistoryNotFoundException::new);
        return new AnalysisHistoryDetail(entry, classify(catalog(userId), entry.detectedSkills()));
    }

    @Override
    public void deleteHistoryEntry(Long userId, Long id) {
        if (!historyRepository.deleteByIdAndUserId(id, userId)) {
            throw new AnalysisHistoryNotFoundException();
        }
    }

    @Override
    public void clearHistory(Long userId) {
        historyRepository.deleteByUserId(userId);
    }

    /**
     * Guarda el análisis y deja una sola entrada por oferta y como mucho MAX_HISTORY por usuario. Un fallo aquí
     * no tumba el análisis (ya gastó cuota): se registra y se devuelve null. synchronized: dos análisis
     * simultáneos no se pisan al recortar.
     */
    private synchronized Long record(Long userId, String text, String textHash, String engine, JobMatch match) {
        try {
            AnalysisHistory saved = historyRepository.save(new AnalysisHistory(null, userId,
                    JobDescriptionText.title(text), text, textHash, engine, match, clock.instant()));
            historyRepository.deleteByTextHashExcept(userId, textHash, saved.id());
            historyRepository.keepLatest(userId, MAX_HISTORY);
            return saved.id();
        } catch (RuntimeException e) {
            // Solo el tipo: el mensaje de la base puede incluir el texto de la oferta.
            log.warn("No se pudo guardar el análisis en el historial: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /** Índice del catálogo por nombre normalizado para comparar sin distinguir mayúsculas. */
    private Map<String, Skill> catalog(Long userId) {
        Map<String, Skill> catalog = new LinkedHashMap<>();
        userSkills.findAll(userId).forEach(s -> catalog.putIfAbsent(normalize(s.getName()), s));
        return catalog;
    }

    private static JobMatch classify(Map<String, Skill> catalog, List<String> required) {
        List<String> mastered = new ArrayList<>();
        List<String> inProgress = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String name : required) {
            String key = normalize(name);
            if (key.isEmpty() || !seen.add(key)) {
                continue;
            }
            Skill skill = catalog.get(key);
            if (skill == null) {
                missing.add(name.strip());
                continue;
            }
            switch (skill.getStatus()) {
                case MASTERED -> mastered.add(skill.getName());
                case IN_PROGRESS -> inProgress.add(skill.getName());
                case PENDING -> missing.add(skill.getName());
            }
        }

        return new JobMatch(matchPercentage(mastered.size(), inProgress.size(), seen.size()),
                mastered, inProgress, missing);
    }

    private static int matchPercentage(int mastered, int inProgress, int total) {
        if (total == 0) {
            return 0;
        }
        return (int) Math.round((mastered + inProgress * IN_PROGRESS_WEIGHT) * 100 / total);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }
}
