package com.skillmap.api.application.service.impl;

import com.skillmap.api.domain.exception.SkillNotFoundException;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SkillStatus;
import com.skillmap.api.domain.model.UserSkill;
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
        Map<Long, UserSkill> progress = userSkillRepository.findByUserId(userId);
        return skillRepository.findAll().stream()
                .map(skill -> withUserProgress(skill, progress.get(skill.getId())))
                .toList();
    }

    public Skill findById(Long userId, Long skillId) {
        Skill skill = skillRepository.findById(skillId).orElseThrow(() -> new SkillNotFoundException(skillId));
        return withUserProgress(skill, userSkillRepository.find(userId, skillId).orElse(null));
    }

    /** Estado del usuario y cuándo pasó a él; sin progreso, pendiente y sin fecha. */
    private static Skill withUserProgress(Skill skill, UserSkill progress) {
        return progress == null
                ? skill.withStatus(SkillStatus.PENDING)
                : skill.withProgress(progress.status(), progress.updatedAt());
    }
}
