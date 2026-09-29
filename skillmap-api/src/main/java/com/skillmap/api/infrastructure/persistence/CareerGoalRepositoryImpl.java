package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.domain.model.CareerGoal;
import com.skillmap.api.domain.repository.CareerGoalRepository;
import com.skillmap.api.infrastructure.persistence.mapper.CareerGoalPersistenceMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CareerGoalRepositoryImpl implements CareerGoalRepository {

    private final CareerGoalJpaRepository jpaRepository;

    public CareerGoalRepositoryImpl(CareerGoalJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<CareerGoal> findAll() {
        return jpaRepository.findAll().stream().map(CareerGoalPersistenceMapper::toDomain).toList();
    }

    @Override
    public Optional<CareerGoal> findById(Long id) {
        return jpaRepository.findById(id).map(CareerGoalPersistenceMapper::toDomain);
    }

    @Override
    public CareerGoal save(CareerGoal goal) {
        var saved = jpaRepository.save(CareerGoalPersistenceMapper.toEntity(goal));
        return CareerGoalPersistenceMapper.toDomain(saved);
    }
}
