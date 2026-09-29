package com.skillmap.api.infrastructure.persistence.mapper;

import com.skillmap.api.domain.factory.SkillFactory;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.infrastructure.persistence.entity.SkillEntity;

/** Traduce entre el modelo de dominio (Skill) y el modelo de persistencia (SkillEntity). */
public final class SkillPersistenceMapper {

    private SkillPersistenceMapper() {
    }

    public static Skill toDomain(SkillEntity entity) {
        return SkillFactory.reconstruct(
                entity.getId(),
                entity.getName(),
                entity.getCategory(),
                entity.getDemandPercentage(),
                entity.getStatus()
        );
    }

    public static SkillEntity toEntity(Skill skill) {
        return new SkillEntity(
                skill.getId(),
                skill.getName(),
                skill.getCategory(),
                skill.getDemandPercentage(),
                skill.getStatus()
        );
    }
}
