package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.domain.model.SkillStatus;
import com.skillmap.api.domain.model.UserSkill;
import com.skillmap.api.domain.repository.UserSkillRepository;
import com.skillmap.api.infrastructure.persistence.entity.UserSkillEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class UserSkillRepositoryImpl implements UserSkillRepository {

    private final UserSkillJpaRepository jpaRepository;

    public UserSkillRepositoryImpl(UserSkillJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Map<Long, SkillStatus> findStatusesByUserId(Long userId) {
        return jpaRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserSkillEntity::getSkillId, UserSkillEntity::getStatus));
    }

    @Override
    @Transactional
    public void save(UserSkill userSkill) {
        Long id = jpaRepository.findByUserIdAndSkillId(userSkill.userId(), userSkill.skillId())
                .map(UserSkillEntity::getId)
                .orElse(null);
        jpaRepository.save(new UserSkillEntity(id, userSkill.userId(), userSkill.skillId(), userSkill.status()));
    }
}
