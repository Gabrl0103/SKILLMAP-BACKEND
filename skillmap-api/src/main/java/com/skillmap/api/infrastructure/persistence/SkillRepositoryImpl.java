package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.repository.SkillRepository;
import com.skillmap.api.infrastructure.persistence.mapper.SkillPersistenceMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Patrón Repository / Adapter: implementa el puerto SkillRepository que pide
 * el dominio, apoyándose en Spring Data JPA por debajo. Si mañana cambiamos
 * de motor de base de datos, solo se toca esta clase.
 */
@Repository
public class SkillRepositoryImpl implements SkillRepository {

    private final SkillJpaRepository jpaRepository;

    public SkillRepositoryImpl(SkillJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Skill> findAll() {
        return jpaRepository.findAll().stream()
                .map(SkillPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Skill> findById(Long id) {
        return jpaRepository.findById(id).map(SkillPersistenceMapper::toDomain);
    }

    @Override
    public Skill save(Skill skill) {
        var savedEntity = jpaRepository.save(SkillPersistenceMapper.toEntity(skill));
        return SkillPersistenceMapper.toDomain(savedEntity);
    }
}
