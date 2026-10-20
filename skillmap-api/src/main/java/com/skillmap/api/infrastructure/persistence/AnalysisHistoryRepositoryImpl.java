package com.skillmap.api.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillmap.api.domain.model.AnalysisHistory;
import com.skillmap.api.domain.model.JobMatch;
import com.skillmap.api.domain.repository.AnalysisHistoryRepository;
import com.skillmap.api.infrastructure.persistence.entity.AnalysisHistoryEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class AnalysisHistoryRepositoryImpl implements AnalysisHistoryRepository {

    private final AnalysisHistoryJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public AnalysisHistoryRepositoryImpl(AnalysisHistoryJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public AnalysisHistory save(AnalysisHistory entry) {
        JobMatch result = entry.result();
        return toDomain(jpaRepository.save(new AnalysisHistoryEntity(entry.id(), entry.userId(), entry.title(),
                entry.jobDescription(), entry.textHash(), entry.engine(), result.matchPercentage(),
                writeSkills(result), entry.createdAt())));
    }

    @Override
    public List<AnalysisHistory> findLatest(Long userId, int limit) {
        return jpaRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, limit))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<AnalysisHistory> findByIdAndUserId(Long id, Long userId) {
        return jpaRepository.findByIdAndUserId(id, userId).map(this::toDomain);
    }

    @Override
    public Optional<AnalysisHistory> findLatestByTextHash(Long userId, String textHash) {
        return jpaRepository.findFirstByUserIdAndTextHashOrderByCreatedAtDescIdDesc(userId, textHash)
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public boolean deleteByIdAndUserId(Long id, Long userId) {
        return jpaRepository.deleteByIdAndUserId(id, userId) > 0;
    }

    @Override
    @Transactional
    public void deleteByTextHashExcept(Long userId, String textHash, Long keepId) {
        jpaRepository.deleteByUserIdAndTextHashAndIdNot(userId, textHash, keepId);
    }

    @Override
    @Transactional
    public void keepLatest(Long userId, int max) {
        if (jpaRepository.countByUserId(userId) <= max) {
            return;
        }
        List<Long> ids = jpaRepository.findIdsNewestFirst(userId);
        jpaRepository.deleteAllByIdInBatch(ids.subList(max, ids.size()));
    }

    @Override
    @Transactional
    public void deleteByUserId(Long userId) {
        jpaRepository.deleteByUserId(userId);
    }

    private AnalysisHistory toDomain(AnalysisHistoryEntity entity) {
        StoredSkills skills = readSkills(entity.getSkillsJson());
        JobMatch result = new JobMatch(entity.getMatchPercentage(), skills.mastered(), skills.inProgress(),
                skills.missing());
        return new AnalysisHistory(entity.getId(), entity.getUserId(), entity.getTitle(), entity.getJobDescription(),
                entity.getTextHash(), entity.getEngine(), result, entity.getCreatedAt());
    }

    private String writeSkills(JobMatch result) {
        try {
            return objectMapper.writeValueAsString(
                    new StoredSkills(result.mastered(), result.inProgress(), result.missing()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudieron guardar las habilidades del análisis", e);
        }
    }

    private StoredSkills readSkills(String json) {
        try {
            return objectMapper.readValue(json, StoredSkills.class);
        } catch (JsonProcessingException e) {
            // Sin la causa: el mensaje de Jackson incluye un trozo del JSON.
            throw new IllegalStateException("Las habilidades guardadas de un análisis no son válidas");
        }
    }

    /** Lo que va en skills_json: las tres listas del resultado, en ese orden. */
    record StoredSkills(List<String> mastered, List<String> inProgress, List<String> missing) {
        StoredSkills {
            mastered = mastered == null ? List.of() : List.copyOf(mastered);
            inProgress = inProgress == null ? List.of() : List.copyOf(inProgress);
            missing = missing == null ? List.of() : List.copyOf(missing);
        }
    }
}
