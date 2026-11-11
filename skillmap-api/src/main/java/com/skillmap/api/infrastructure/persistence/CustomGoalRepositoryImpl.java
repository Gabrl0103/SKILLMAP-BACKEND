package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.domain.exception.CustomGoalConflictException;
import com.skillmap.api.domain.factory.CustomGoalFactory;
import com.skillmap.api.domain.model.CustomGoal;
import com.skillmap.api.domain.repository.CustomGoalRepository;
import com.skillmap.api.infrastructure.persistence.entity.CustomGoalEntity;
import com.skillmap.api.infrastructure.persistence.entity.CustomGoalSkillEntity;
import com.skillmap.api.infrastructure.persistence.entity.SkillEntity;
import com.skillmap.api.infrastructure.persistence.entity.UserEntity;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public class CustomGoalRepositoryImpl implements CustomGoalRepository {

    private final CustomGoalJpaRepository jpaRepository;
    private final EntityManager entityManager;

    public CustomGoalRepositoryImpl(CustomGoalJpaRepository jpaRepository, EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomGoal> findByUserId(Long userId) {
        return jpaRepository.findByUserIdOrderByIdAsc(userId).stream().map(CustomGoalRepositoryImpl::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomGoal> find(Long userId, Long id) {
        return jpaRepository.findByIdAndUserId(id, userId).map(CustomGoalRepositoryImpl::toDomain);
    }

    @Override
    @Transactional
    public CustomGoal save(CustomGoal goal) {
        CustomGoalEntity entity = goal.id() == null
                ? new CustomGoalEntity(entityManager.getReference(UserEntity.class, goal.userId()), goal.name(),
                        CustomGoalFactory.nameKey(goal.name()), goal.description(), goal.createdAt())
                : jpaRepository.findByIdAndUserId(goal.id(), goal.userId()).orElseThrow();
        List<SkillEntity> skills = goal.skillIds().stream()
                .map(id -> entityManager.getReference(SkillEntity.class, id))
                .toList();
        entity.update(goal.name(), CustomGoalFactory.nameKey(goal.name()), goal.description(), skills, goal.updatedAt());
        try {
            return toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // Dos guardados a la vez con el mismo nombre: lo frena el índice único (user_id, name_key). Cualquier otra
            // violación no es un nombre repetido y sale tal cual (500).
            String cause = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
            if (cause.contains("uk_custom_goals_user_name")) {
                throw new CustomGoalConflictException("Ya tienes un objetivo con ese nombre. Elige otro.");
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        jpaRepository.findByIdAndUserId(id, userId).ifPresent(jpaRepository::delete);
    }

    @Override
    @Transactional
    public void deleteByUserId(Long userId) {
        jpaRepository.deleteByUserId(userId);
    }

    private static CustomGoal toDomain(CustomGoalEntity e) {
        List<Long> skillIds = e.getSkills().stream().map(CustomGoalSkillEntity::getSkillId).toList();
        return new CustomGoal(e.getId(), e.getUserId(), e.getName(), e.getDescription(), skillIds, e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
