package com.skillmap.api.application.service.impl;

import com.skillmap.api.domain.exception.SkillNotFoundException;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SkillStatus;
import com.skillmap.api.domain.repository.SkillRepository;
import com.skillmap.api.domain.repository.UserSkillRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * El catálogo de habilidades (nombre, categoría, demanda) es global; el estado de cada una
 * es el del usuario. Sin progreso guardado, la habilidad está pendiente: el estado que trae
 * la tabla skills (el del seed) ya no se aplica a nadie.
 */
@Component
public class UserSkillCatalog {

    private final SkillRepository skillRepository;
    private final UserSkillRepository userSkillRepository;

    public UserSkillCatalog(SkillRepository skillRepository, UserSkillRepository userSkillRepository) {
        this.skillRepository = skillRepository;
        this.userSkillRepository = userSkillRepository;
    }

    public List<Skill> findAll(Long userId) {
        Map<Long, SkillStatus> statuses = userSkillRepository.findStatusesByUserId(userId);
        return skillRepository.findAll().stream()
                .map(skill -> withUserStatus(skill, statuses))
                .toList();
    }

    public Skill findById(Long userId, Long skillId) {
        Skill skill = skillRepository.findById(skillId).orElseThrow(() -> new SkillNotFoundException(skillId));
        return withUserStatus(skill, userSkillRepository.findStatusesByUserId(userId));
    }

    private static Skill withUserStatus(Skill skill, Map<Long, SkillStatus> statuses) {
        return skill.withStatus(statuses.getOrDefault(skill.getId(), SkillStatus.PENDING));
    }
}
